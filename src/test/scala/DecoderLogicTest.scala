import org.scalatest._
import chiseltest._
import chisel3._
import op_a_sel_e._
import op_b_sel_e._
import alu_op_e._

class DecoderLogicTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "ALU"
  it should "do ALU_ADD" in {
    test(new ibex_decoder) { c =>
      c.io.branch_taken_i.poke(false.B)
      c.io.instr_first_cycle_i.poke(true.B)
      c.io.instr_rdata_i.poke("b0000000_11111_00000_000_00000_0110011".U)
      c.io.instr_rdata_alu_i.poke("b0000000_11111_00000_000_00000_0110011".U)

      c.io.rf_ren_a_o.expect(true.B)
      c.io.rf_ren_b_o.expect(true.B)
      c.io.rf_ren_b_o.expect(true.B)
      c.io.rf_we_o.expect(true.B)

      c.io.alu_op_a_mux_sel_o.expect(OP_A_REG_A)
      c.io.alu_op_b_mux_sel_o.expect(OP_B_REG_B)
      c.io.alu_operator_o.expect(ALU_ADD)
    }
  }
}
