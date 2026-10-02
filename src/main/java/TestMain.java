import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import opencep.CEP;
import opencep.base.*;
import opencep.condition.*;
import opencep.plugin.stocks.MetastockDataFormatter;
import opencep.stream.*;

/** Run with ./scripts/run.sh, or pass an input path and optional output path. */
public final class TestMain {
  public static void main(String[] args) throws Exception {
    if (args.length > 2) {
      System.err.println("Usage: TestMain [metastock-input-file [matches-output-file]]");
      System.exit(2);
    }
    Pattern risingGooglePrices =
        new Pattern(
            new SeqOperator(
                new PrimitiveEventStructure("GOOG", "a"),
                new PrimitiveEventStructure("GOOG", "b"),
                new PrimitiveEventStructure("GOOG", "c")),
            new AndCondition(
                new SmallerThanCondition(
                    new Variable("a", "Peak Price"), new Variable("b", "Peak Price")),
                new SmallerThanCondition(
                    new Variable("b", "Peak Price"), new Variable("c", "Peak Price"))),
            Duration.ofMinutes(3));
    CEP cep = new CEP(risingGooglePrices);
    if (args.length == 0) {
      List<String> input =
          List.of(
              "GOOG,202001010900,100,101,99,100,1000",
              "GOOG,202001010901,101,102,100,101,1200",
              "AMZN,202001010901,70,72,69,71,500",
              "GOOG,202001010902,102,103,101,102,1400");
      OutputStream<PatternMatch> output = new OutputStream<>();
      double seconds = cep.run(input, output, new MetastockDataFormatter());
      for (PatternMatch match : output.snapshot()) System.out.print(match);
      if (output.count() != 1) throw new IllegalStateException("Demo expected exactly one match");
      System.out.printf("Detected %d match in %.6f seconds%n", output.count(), seconds);
    } else {
      try (FileInputStream input = new FileInputStream(Path.of(args[0]))) {
        if (args.length == 2) {
          var output = new FileOutputStream<PatternMatch>(Path.of(args[1]));
          double seconds = cep.run(input, output, new MetastockDataFormatter());
          System.out.printf("Wrote matches to %s in %.6f seconds%n", args[1], seconds);
        } else {
          OutputStream<PatternMatch> output = new OutputStream<>();
          double seconds = cep.run(input, output, new MetastockDataFormatter());
          for (PatternMatch match : output.snapshot()) System.out.print(match);
          System.out.printf("Detected %d matches in %.6f seconds%n", output.count(), seconds);
        }
      }
    }
    System.out.println("Evaluation plan: " + cep.getEvaluationMechanismStructureSummary());
  }
}
