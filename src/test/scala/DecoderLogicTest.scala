import org.scalatest._
import chiseltest._
import chisel3._
import op_a_sel_e._
import op_b_sel_e._
import alu_op_e._

class DecoderLogicTest extends FlatSpec with ChiselScalatestTester with Matchers {

  private def ArithmeticInstructions(c: ibex_decoder, operation: alu_op_e.Type, str1: String, str2: String): Unit = {
    c.io.branch_taken_i.poke(false.B)
    c.io.instr_first_cycle_i.poke(true.B)

    val rs2 = "11111"
    val rs1 = "00000"
    val rd = "00000"
    val instruction: String = "b" + str1 + rs2 + rs1 + str2 + rd + "0110011"

    c.io.instr_rdata_i.poke(instruction.U)
    c.io.instr_rdata_alu_i.poke(instruction.U)

    c.io.rf_ren_a_o.expect(true.B)
    c.io.rf_ren_b_o.expect(true.B)
    c.io.rf_we_o.expect(true.B)

    c.io.alu_op_a_mux_sel_o.expect(OP_A_REG_A)
    c.io.alu_op_b_mux_sel_o.expect(OP_B_REG_B)

    c.io.alu_operator_o.expect(operation)
  }

  private def ConditionalMovesInstructions(c: ibex_decoder, operation: alu_op_e.Type, str2: String): Unit = {
    c.io.branch_taken_i.poke(false.B)
    c.io.instr_first_cycle_i.poke(true.B)

    val imm1 = "0000000"
    val rs2 = "11111"
    val rs1 = "00000"
    val imm2 = "00000"
    val instruction: String = "b" + imm1 + rs2 + rs1 + str2 + imm2 + "1100011"

    c.io.instr_rdata_i.poke(instruction.U)
    c.io.instr_rdata_alu_i.poke(instruction.U)

    c.io.rf_ren_a_o.expect(true.B)
    c.io.rf_ren_b_o.expect(true.B)
    c.io.rf_we_o.expect(false.B)

    c.io.branch_in_dec_o.expect((true.B))

    c.io.alu_op_a_mux_sel_o.expect(OP_A_REG_A)
    c.io.alu_op_b_mux_sel_o.expect(OP_B_REG_B)

    c.io.alu_operator_o.expect(operation)
  }

  behavior of "Decode Arithmetic Instructions"
  it should "do ALU_ADD" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_ADD, "0000000", "000")
    }
  }
  it should "do ALU_SUB" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_SUB, "0100000", "000")
    }
  }
  it should "do ALU_SLL" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_SLL, "0000000", "001")
    }
  }
  it should "do ALU_SLT" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_SLT, "0000000", "010")
    }
  }
  it should "do ALU_SLTU" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_SLTU, "0000000", "011")
    }
  }
  it should "do ALU_SRL" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_SRL, "0000000", "101")
    }
  }
  it should "do ALU_AND" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_AND, "0000000", "111")
    }
  }
  it should "do ALU_OR" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_OR, "0000000", "110")
    }
  }
  it should "do ALU_XOR" in {
    test(new ibex_decoder) { c =>
      ArithmeticInstructions(c, ALU_XOR, "0000000", "100")
    }
  }

  behavior of "Decode Conditional Moves Instructions"
  it should "do ALU_EQ" in {
    test(new ibex_decoder) { c =>
      ConditionalMovesInstructions(c, ALU_EQ, "000")
    }
  }
  it should "do ALU_NE" in {
    test(new ibex_decoder) { c =>
      ConditionalMovesInstructions(c, ALU_NE, "001")
    }
  }
  it should "do ALU_LT" in {
    test(new ibex_decoder) { c =>
      ConditionalMovesInstructions(c, ALU_LT, "100")
    }
  }
  it should "do ALU_GE" in {
    test(new ibex_decoder) { c =>
      ConditionalMovesInstructions(c, ALU_GE, "101")
    }
  }
  it should "do ALU_LTU" in {
    test(new ibex_decoder) { c =>
      ConditionalMovesInstructions(c, ALU_LTU, "110")
    }
  }
  it should "do ALU_GEU" in {
    test(new ibex_decoder) { c =>
      ConditionalMovesInstructions(c, ALU_GEU, "111")
    }
  }
}
