package corecer.runtime.profiling;

public class Profiler {
    static long compileTime = 0;
    static long enumerationTime = 0;
    static long executionTime = 0;
    static long numberOfMatches = 0;
    static long cleanUps = 0;

    public static void reset() {
        compileTime = enumerationTime = executionTime = numberOfMatches = cleanUps = 0;
    }

    public static long getCompileTime() { return compileTime; }
    public static long getEnumerationTime() { return enumerationTime; }
    public static long getExecutionTime() { return executionTime; }
    public static long getNumberOfMatches() { return numberOfMatches; }
    public static long getCleanUps() { return cleanUps; }

    public static void addCompileTime(long time) {
        compileTime += time;
    }

    public static void addEnumerationTime(long time) {
        enumerationTime += time;
    }

    public static void addExecutionTime(long time) {
        executionTime += time;
    }

    public static void incrementMatches() {
        numberOfMatches++;
    }

    public static void incrementCleanUps() {
        cleanUps++;
    }

    public static void print(){
//        System.out.print((double)compileTime/1000000000 + ",");
//        System.out.print((double)executionTime/1000000000 + ",");
        System.out.print((double)enumerationTime/1000000000 + ",");
        System.out.print(numberOfMatches);
//        System.err.println("Number of cleanups: " + cleanUps);
    }
}
