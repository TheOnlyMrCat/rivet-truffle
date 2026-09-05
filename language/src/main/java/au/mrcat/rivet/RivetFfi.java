package au.mrcat.rivet;

import au.mrcat.rivet.riscv.AccessType;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;

public final class RivetFfi {
    private final static Linker linker = Linker.nativeLinker();
    private static final ArrayList<RivetFfi> instances = new ArrayList<>();

    private static RivetFfi getInstance(int instance) {
        synchronized (instances) {
            return instances.get(instance);
        }
    }

    static {
        System.loadLibrary("slirp");
        System.loadLibrary("rivet");
    }

    private static final SymbolLookup symbolLookup = SymbolLookup.loaderLookup();
    private static final MethodHandle rust_create_devices = linker.downcallHandle(
            symbolLookup.findOrThrow("rust_create_devices"),
            FunctionDescriptor.of(ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS));
    private static final MethodHandle rust_load = linker.downcallHandle(
            symbolLookup.findOrThrow("rust_load"),
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_BYTE));
    private static final MethodHandle rust_store = linker.downcallHandle(
            symbolLookup.findOrThrow("rust_store"),
            FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_BYTE));
    private static final MethodHandle rust_get_time = linker.downcallHandle(
            symbolLookup.findOrThrow("rust_get_time"),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS));

    private static final MethodHandle memoryReadHandle = initMemoryReadHandle();
    private static MethodHandle initMemoryReadHandle() {
        MethodHandle ch = null;
        try {
            ch = MethodHandles.lookup()
                    .findStatic(RivetFfi.class,
                            "memoryReadStatic",
                            MethodType.methodType(boolean.class, int.class, long.class, long.class, MemorySegment.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return ch;
    }

    public static boolean memoryReadStatic(int instance, long addr, long len, MemorySegment buffer) {
        return getInstance(instance).memoryRead(addr, len, buffer);
    }

    private static final MethodHandle memoryWriteHandle = initMemoryWriteHandle();
    private static MethodHandle initMemoryWriteHandle() {
        MethodHandle ch = null;
        try {
            ch = MethodHandles.lookup()
                    .findStatic(RivetFfi.class,
                            "memoryWriteStatic",
                            MethodType.methodType(boolean.class, int.class, long.class, long.class, MemorySegment.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return ch;
    }

    public static boolean memoryWriteStatic(int instance, long addr, long len, MemorySegment buffer) {
        return getInstance(instance).memoryWrite(addr, len, buffer);
    }


    private static final MethodHandle hartTriggerHandle = initHartTriggerHandle();
    private static MethodHandle initHartTriggerHandle() {
        MethodHandle ch = null;
        try {
            ch = MethodHandles.lookup()
                    .findStatic(RivetFfi.class,
                            "hartTriggerStatic",
                            MethodType.methodType(void.class, int.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return ch;
    }

    public static void hartTriggerStatic(int instance, int interrupt) {
        getInstance(instance).hartTrigger(interrupt);
    }


    private static final MethodHandle hartUntriggerHandle = initHartUntriggerHandle();
    private static MethodHandle initHartUntriggerHandle() {
        MethodHandle ch = null;
        try {
            ch = MethodHandles.lookup()
                    .findStatic(RivetFfi.class,
                            "hartUntriggerStatic",
                            MethodType.methodType(void.class, int.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return ch;
    }

    public static void hartUntriggerStatic(int instance, int interrupt) {
        getInstance(instance).hartUntrigger(interrupt);
    }


    private final MemorySegment devices;
    private final RivetContext ctx;

    public RivetFfi(RivetContext ctx) {
        this.ctx = ctx;

        // Save this instance to the static array
        int instance;
        synchronized (instances) {
            instance = instances.size();
            instances.add(this);
        }

        // Create the upcall function pointers
        FunctionDescriptor memoryReadDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_BYTE));
        MemorySegment memoryReadFunc = linker.upcallStub(memoryReadHandle,
                memoryReadDesc,
                Arena.global());
        FunctionDescriptor memoryWriteDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_BYTE));
        MemorySegment memoryWriteFunc = linker.upcallStub(memoryWriteHandle,
                memoryWriteDesc,
                Arena.global());
        FunctionDescriptor hartTriggerDesc = FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT);
        MemorySegment hartTriggerFunc = linker.upcallStub(hartTriggerHandle,
                hartTriggerDesc,
                Arena.global());
        FunctionDescriptor hartUntriggerDesc = FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT);
        MemorySegment hartUntriggerFunc = linker.upcallStub(hartUntriggerHandle,
                hartUntriggerDesc,
                Arena.global());

        try {
            devices = (MemorySegment) rust_create_devices.invoke(instance, 1, memoryReadFunc, memoryWriteFunc, hartTriggerFunc, hartUntriggerFunc);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        if (devices.address() == 0) {
            throw new RuntimeException("Failed to initialise Rivet FFI devices");
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    public long rustLoad(long addr, byte bits, AccessType accessType) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment value = a.allocate(8, 8);
            if ((boolean) rust_load.invoke(devices, addr, value, bits)) {
                return value.get(ValueLayout.JAVA_LONG, 0);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        throw new RiscvTrapException(accessType.accessFaultCause);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    public void rustStore(long addr, long value, byte bits) {
        try {
            if ((boolean) rust_store.invoke(devices, addr, value, bits)) {
                return;
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        throw new RiscvTrapException(ExceptionCause.StoreAmoAccessFault);
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    public long rustGetTime() {
        try {
            return (long) rust_get_time.invoke(devices);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private boolean memoryRead(long addr, long len, MemorySegment buffer) {
        if (!ctx.physicalMemory.isInPhysicalMemory(addr, len)) {
            return false;
        }
        var byteBuffer = buffer.reinterpret(len).asByteBuffer();
        for (int i = 0; i < len; i++) {
            byteBuffer.put(i, ctx.physicalMemory.readByte(addr + i));
        }
        return true;
    }

    private boolean memoryWrite(long addr, long len, MemorySegment buffer) {
        if (!ctx.physicalMemory.isInPhysicalMemory(addr, len)) {
            return false;
        }
        var byteBuffer = buffer.reinterpret(len).asByteBuffer();
        for (int i = 0; i < len; i++) {
            ctx.physicalMemory.writeByte(addr + i, byteBuffer.get(i));;
        }
        return true;
    }

    private void hartTrigger(int interrupt) {
        ctx.privilegedState.trigger(interrupt);
    }

    private void hartUntrigger(int interrupt) {
        ctx.privilegedState.untrigger(interrupt);
    }
}
