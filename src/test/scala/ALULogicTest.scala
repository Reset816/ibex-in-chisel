import Chisel.Fill
import org.scalatest._
import chiseltest._
import chisel3._

class ALULogicTest extends FlatSpec with ChiselScalatestTester with Matchers {

  /**
   * @param x A positive number which should be a negative number
   * @return SInt.asUInt()
   */
  private def ToNegativeSInt(x: Int): Int = scala.math.pow(2, 32).toInt - x

  behavior of "Arithmetics"
  it should "do ALU_ADD" in {
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_ADD)
      c.io.operand_a_i.poke(5.U)
      c.io.operand_b_i.poke(10.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(15.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_ADD)
      c.io.operand_a_i.poke(1.U)
      c.io.operand_b_i.poke(2.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(3.U)
    }
    test(new ibex_alu) { c => // Fill(33, true.B) + 1 = 0
      c.io.operator_i.poke(alu_op_e.ALU_ADD)
      c.io.operand_a_i.poke(4294967295L.U)
      c.io.operand_b_i.poke(1.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
  }
  it should "do ALU_SUB" in {
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_SUB)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(4.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(6.U)
    }
    test(new ibex_alu) { c => // 0 - 1 = Fill(33, true.B)
      c.io.operator_i.poke(alu_op_e.ALU_SUB)
      c.io.operand_a_i.poke(0.U)
      c.io.operand_b_i.poke(1.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(4294967295L.U)
    }
  }

  behavior of "Shifts"
  it should "do ALU_SRA" in {
    test(new ibex_alu) { c => // 0110 > 1 = 0011
      c.io.operator_i.poke(alu_op_e.ALU_SRA)
      c.io.operand_a_i.poke(6.U)
      c.io.operand_b_i.poke(1.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(3.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_SRA)
      c.io.operand_a_i.poke(2147483648L.U)
      c.io.operand_b_i.poke(1.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(3221225472L.U)
    }
  }
  it should "do ALU_SRL" in {
    test(new ibex_alu) { c => // 1010 > 2 = 2
      c.io.operator_i.poke(alu_op_e.ALU_SRL)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(2.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(2.U)
    }
  }
  it should "do ALU_SLL" in {
    test(new ibex_alu) { c => // 1010 < 2 = 101000
      c.io.operator_i.poke(alu_op_e.ALU_SLL)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(2.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(40.U)
    }
    test(new ibex_alu) { c => // 1 < 32 = 0
      c.io.operator_i.poke(alu_op_e.ALU_SLL)
      c.io.operand_a_i.poke(1.U)
      c.io.operand_b_i.poke(32.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
  }

  behavior of "Comparisons"
  it should "do ALU_LT" in {
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LT)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LT)
      c.io.operand_a_i.poke(8.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LT)
      c.io.operand_a_i.poke(8.U)
      c.io.operand_b_i.poke(8.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LT)
      c.io.operand_a_i.poke(ToNegativeSInt(8).U)
      c.io.operand_b_i.poke(ToNegativeSInt(7).U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LT)
      c.io.operand_a_i.poke(ToNegativeSInt(8).U)
      c.io.operand_b_i.poke(ToNegativeSInt(9).U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LT)
      c.io.operand_a_i.poke(ToNegativeSInt(8).U)
      c.io.operand_b_i.poke(ToNegativeSInt(8).U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
  }
  it should "do ALU_LTU" in {
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LTU)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LTU)
      c.io.operand_a_i.poke(8.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_LTU)
      c.io.operand_a_i.poke(8.U)
      c.io.operand_b_i.poke(8.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
  }
  it should "do ALU_GE" in {
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GE)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GE)
      c.io.operand_a_i.poke(8.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GE)
      c.io.operand_a_i.poke(8.U)
      c.io.operand_b_i.poke(8.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GE)
      c.io.operand_a_i.poke(ToNegativeSInt(8).U)
      c.io.operand_b_i.poke(ToNegativeSInt(7).U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GE)
      c.io.operand_a_i.poke(ToNegativeSInt(8).U)
      c.io.operand_b_i.poke(ToNegativeSInt(9).U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GE)
      c.io.operand_a_i.poke(ToNegativeSInt(8).U)
      c.io.operand_b_i.poke(ToNegativeSInt(8).U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
  }
  it should "do ALU_GEU" in {
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GEU)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GEU)
      c.io.operand_a_i.poke(8.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_GEU)
      c.io.operand_a_i.poke(8.U)
      c.io.operand_b_i.poke(8.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
  }
  it should "do ALU_EQ" in {
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_EQ)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(10.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_EQ)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
  }
  it should "do ALU_NE" in {
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_NE)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(10.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(0.U)
    }
    test(new ibex_alu) { c =>
      c.io.operator_i.poke(alu_op_e.ALU_NE)
      c.io.operand_a_i.poke(10.U)
      c.io.operand_b_i.poke(9.U)
      c.io.multdiv_sel_i.poke(false.B)
      c.io.result_o.expect(1.U)
    }
  }
}
