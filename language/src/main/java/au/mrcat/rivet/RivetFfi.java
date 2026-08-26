package au.mrcat.rivet;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;

public final class RivetFfi {
    private final static Linker linker = Linker.nativeLinker();
    private static ArrayList<RivetFfi> instances = new ArrayList<>();

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
        return instances.get(instance).memoryRead(addr, len, buffer);
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
        return instances.get(instance).memoryWrite(addr, len, buffer);
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
        instances.get(instance).hartTrigger(interrupt);
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
        instances.get(instance).hartUntrigger(interrupt);
    }


    private final MemorySegment devices;
    private final RivetContext ctx;

    public RivetFfi(RivetContext ctx) {
        this.ctx = ctx;

        // Save this instance to the static array
        int instance = instances.size();
        instances.add(this);

        // Create the upcall function pointers
        FunctionDescriptor memoryReadDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_BYTE));
        MemorySegment memoryReadFunc = linker.upcallStub(memoryReadHandle,
                memoryReadDesc,
                Arena.ofAuto());
        FunctionDescriptor memoryWriteDesc = FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN,
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_LONG,
                ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_BYTE));
        MemorySegment memoryWriteFunc = linker.upcallStub(memoryWriteHandle,
                memoryWriteDesc,
                Arena.ofAuto());
        FunctionDescriptor hartTriggerDesc = FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT);
        MemorySegment hartTriggerFunc = linker.upcallStub(hartTriggerHandle,
                hartTriggerDesc,
                Arena.ofAuto());
        FunctionDescriptor hartUntriggerDesc = FunctionDescriptor.ofVoid(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT);
        MemorySegment hartUntriggerFunc = linker.upcallStub(hartUntriggerHandle,
                hartUntriggerDesc,
                Arena.ofAuto());

        try {
            devices = (MemorySegment) rust_create_devices.invoke(instance, 1, memoryReadFunc, memoryWriteFunc, hartTriggerFunc, hartUntriggerFunc);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean memoryRead(long addr, long len, MemorySegment buffer) {
        if (!ctx.physicalMemory.isInPhysicalMemory(addr, len)) {
            return false;
        }
        var byteBuffer = buffer.asByteBuffer();
        for (int i = 0; i < len; i++) {
            byteBuffer.put(i, ctx.physicalMemory.readByte(addr + i));
        }
        return true;
    }

    public boolean memoryWrite(long addr, long len, MemorySegment buffer) {
        if (!ctx.physicalMemory.isInPhysicalMemory(addr, len)) {
            return false;
        }
        var byteBuffer = buffer.asByteBuffer();
        for (int i = 0; i < len; i++) {
            ctx.physicalMemory.writeByte(addr + i, byteBuffer.get(i));;
        }
        return true;
    }

    public void hartTrigger(int interrupt) {
        System.out.println("Hart trigger from " + Thread.currentThread().getName());
    }

    public void hartUntrigger(int interrupt) {
        System.out.println("Hart untrigger from " + Thread.currentThread().getName());
    }
}
