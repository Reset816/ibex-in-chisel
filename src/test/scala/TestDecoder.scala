import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.options.TargetDirAnnotation

//test:runMain TestALU
object TestDecoder extends App {
  (new chisel3.stage.ChiselStage).execute(
    Array("-X", "verilog", "--full-stacktrace"),
    Seq(ChiselGeneratorAnnotation(() => new ibex_decoder()),
      TargetDirAnnotation("generated/ibex_decoder"))
  )
}