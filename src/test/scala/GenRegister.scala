import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.options.TargetDirAnnotation

object GenRegister extends App {
  (new chisel3.stage.ChiselStage).execute(
    Array("-X", "verilog", "--full-stacktrace"),
    Seq(ChiselGeneratorAnnotation(() => new ibex_register_file_fpga()),
      TargetDirAnnotation("generated"))
  )
}