import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.options.TargetDirAnnotation

object GenLSU extends App {
  (new chisel3.stage.ChiselStage).execute(
    Array("-X", "verilog", "--full-stacktrace"),
    Seq(ChiselGeneratorAnnotation(() => new LSU()),
      TargetDirAnnotation("generated"))
  )
}