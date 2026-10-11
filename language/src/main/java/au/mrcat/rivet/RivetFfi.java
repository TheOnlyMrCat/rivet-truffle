package au.mrcat.rivet;

import au.mrcat.rivet.riscv.AccessType;
import au.mrcat.rivet.riscv.ExceptionCause;
import au.mrcat.rivet.riscv.RoundingMode;
import au.mrcat.rivet.runtime.RiscvTrapException;
import com.oracle.truffle.api.CompilerDirectives;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
        System.loadLibrary("softfp");
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
    private static final MethodHandle rust_set_stimecmp = linker.downcallHandle(
            symbolLookup.findOrThrow("rust_set_stimecmp"),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle rust_trigger_serial_interrupt = linker.downcallHandle(
            symbolLookup.findOrThrow("rust_trigger_serial_interrupt"),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
    private static final MethodHandle rust_untrigger_serial_interrupt = linker.downcallHandle(
            symbolLookup.findOrThrow("rust_untrigger_serial_interrupt"),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    private static final MethodHandle add_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("add_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle sub_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("sub_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle mul_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("mul_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle div_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("div_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle sqrt_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("sqrt_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle fma_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("fma_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle min_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("min_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle max_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("max_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle eq_quiet_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("eq_quiet_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle le_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("le_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle lt_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("lt_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle fclass_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("fclass_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle cvt_sf32_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf32_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_sf64_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf64_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_sf32_i32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf32_i32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_sf32_u32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf32_u32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_sf32_i64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf32_i64"),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_sf32_u64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf32_u64"),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_i32_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_i32_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_u32_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_u32_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_i64_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_i64_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_u64_sf32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_u64_sf32"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));

    private static final MethodHandle add_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("add_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle sub_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("sub_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle mul_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("mul_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle div_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("div_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle sqrt_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("sqrt_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle fma_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("fma_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle min_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("min_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle max_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("max_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle eq_quiet_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("eq_quiet_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle le_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("le_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle lt_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("lt_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle fclass_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("fclass_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle cvt_sf64_i64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf64_i64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_sf64_u64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf64_u64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_sf64_i32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf64_i32"),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_sf64_u32 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_sf64_u32"),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_i32_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_i32_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_u32_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_u32_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_i64_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_i64_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS));
    private static final MethodHandle cvt_u64_sf64 = linker.downcallHandle(
            symbolLookup.findOrThrow("cvt_u64_sf64"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
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
    private final Arena arena;
    private final MemorySegment fcsr;
    private final MemorySegment load;

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

        arena = Arena.ofAuto();
        fcsr = arena.allocate(ValueLayout.JAVA_LONG);
        load = arena.allocate(ValueLayout.JAVA_LONG);

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
        try {
            if ((boolean) rust_load.invoke(devices, addr, load, bits)) {
                return load.get(ValueLayout.JAVA_LONG, 0);
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

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    public void rustSetStimecmp(long value) {
        try {
            rust_set_stimecmp.invoke(devices, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    public void rustTriggerSerialInterrupt() {
        try {
            rust_trigger_serial_interrupt.invoke(devices);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    public void rustUntriggerSerialInterrupt() {
        try {
            rust_untrigger_serial_interrupt.invoke(devices);
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

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int add_sf32(int a, int b, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) add_sf32.invoke(a, b, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int sub_sf32(int a, int b, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) sub_sf32.invoke(a, b, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int mul_sf32(int a, int b, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) mul_sf32.invoke(a, b, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int div_sf32(int a, int b, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) div_sf32.invoke(a, b, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int sqrt_sf32(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) sqrt_sf32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int fma_sf32(int a, int b, int c, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) fma_sf32.invoke(a, b, c, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int min_sf32(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) min_sf32.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int max_sf32(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) max_sf32.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int eq_quiet_sf32(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) eq_quiet_sf32.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int le_sf32(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) le_sf32.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int lt_sf32(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) lt_sf32.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int fclass_sf32(int a) {
        try {
            return (int) fclass_sf32.invoke(a);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    long cvt_sf32_sf64(int a) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (long) cvt_sf32_sf64.invoke(a, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_sf64_sf32(long a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_sf64_sf32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_sf32_i32(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_sf32_i32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_sf32_u32(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_sf32_u32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    long cvt_sf32_i64(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (long) cvt_sf32_i64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    long cvt_sf32_u64(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (long) cvt_sf32_u64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_i32_sf32(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_i32_sf32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_u32_sf32(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_u32_sf32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_i64_sf32(long a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_i64_sf32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining =true)
    int cvt_u64_sf32(long a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_u64_sf32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int add_sf64(int a, int b, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) add_sf64.invoke(a, b, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int sub_sf64(int a, int b, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) sub_sf64.invoke(a, b, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int mul_sf64(int a, int b, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) mul_sf64.invoke(a, b, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int div_sf64(int a, int b, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) div_sf64.invoke(a, b, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int sqrt_sf64(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) sqrt_sf64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int fma_sf64(int a, int b, int c, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) fma_sf64.invoke(a, b, c, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int min_sf64(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) min_sf64.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int max_sf64(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) max_sf64.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int eq_quiet_sf64(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) eq_quiet_sf64.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int le_sf64(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) le_sf64.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int lt_sf64(int a, int b) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) lt_sf64.invoke(a, b, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int fclass_sf64(int a) {
        try {
            return (int) fclass_sf64.invoke(a);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_sf64_i32(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_sf64_i32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_sf64_u32(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_sf64_u32.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    long cvt_sf64_i64(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (long) cvt_sf64_i64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    long cvt_sf64_u64(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (long) cvt_sf64_u64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_i32_sf64(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_i32_sf64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_u32_sf64(int a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_u32_sf64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining = true)
    int cvt_i64_sf64(long a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_i64_sf64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @CompilerDirectives.TruffleBoundary(allowInlining =true)
    int cvt_u64_sf64(long a, RoundingMode rm) {
        fcsr.set(ValueLayout.JAVA_LONG, 0, ctx.privilegedState.getFcsr());
        try {
            return (int) cvt_u64_sf64.invoke(a, rm, fcsr);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
