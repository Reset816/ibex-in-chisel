import chisel3._
import chisel3.util.{Cat, Fill, MuxLookup, is, switch}
import alu_op_e._
import rv32b_e._

/**
 * Arithmetic logic unit
 */
class ibex_alu(
                val RV32B: rv32b_e.Type = RV32BNone,
              ) extends Module {
  val io = IO(new Bundle {
    val operator_i: alu_op_e.Type = Input(alu_op_e()) // Reference:https://stackoverflow.com/questions/66165591/chisel-passing-enum-type-as-io
    val operand_a_i: UInt = Input(UInt(32.W))
    val operand_b_i: UInt = Input(UInt(32.W))

    val instr_first_cycle_i: Bool = Input(Bool())

    // val imd_val_q_i: Vec[UInt] = Input(Vec(2, UInt(32.W)))
    //    val imd_val_d_o: Vec[UInt] = Output(Vec(2, UInt(32.W)))
    //    val imd_val_we_o: UInt = Output(UInt(2.W))

    val adder_result_o: UInt = Output(UInt(32.W))
    val adder_result_ext_o: UInt = Output(UInt(34.W)) // todo: 删除这个端口，这个端口作为 div/mul 部件的输入存在

    val result_o: UInt = Output(UInt(32.W))
    val comparison_result_o: Bool = Output(Bool())
    // val is_equal_result_o: Bool = Output(Bool())

  })

  //  var operand_a_rev: UInt = Wire(UInt(32.W))
  var operand_b_neg: UInt = Wire(UInt(33.W))
  //
  //  // bit reverse operand_a for left shifts and bit counting
  //  for (k <- 0 until 32) {
  //    operand_a_rev(k) := io.operand_a_i(31 - k)
  //  }

  ///////////
  // Adder //
  ///////////

  var adder_op_b_negate: Bool = Wire(Bool()) // 判断b是否取反
  var adder_in_a: UInt = Wire(UInt(33.W))
  var adder_in_b: UInt = Wire(UInt(33.W))
  var adder_result: UInt = Wire(UInt(32.W))

  adder_op_b_negate := MuxLookup(io.operator_i.asUInt(), false.B, Array(
    // Adder OPs
    ALU_SUB.asUInt() -> true.B,

    ALU_EQ.asUInt() -> true.B,
    ALU_NE.asUInt() -> true.B,
    ALU_GE.asUInt() -> true.B,
    ALU_GEU.asUInt() -> true.B,
    ALU_LT.asUInt() -> true.B,
    ALU_LTU.asUInt() -> true.B,
    ALU_SLT.asUInt() -> true.B,
    ALU_SLTU.asUInt() -> true.B,

    // Comparator OPs
    ALU_MIN.asUInt() -> true.B,
    ALU_MINU.asUInt() -> true.B,
    ALU_MAX.asUInt() -> true.B,
    ALU_MAXU.asUInt() -> true.B
  ))

  // prepare operand a
  adder_in_a := Cat(io.operand_a_i.asUInt, "b1".U(1.W))

  // prepare operand b
  operand_b_neg := Cat(io.operand_b_i, "b0".U(1.W)) ^ Fill(33, true.B) // Reference: https://stackoverflow.com/questions/56439589/how-to-duplicate-a-single-bit-to-a-uint-in-chisel-3
  when(true.B === adder_op_b_negate) {
    adder_in_b := operand_b_neg
  }.otherwise {
    adder_in_b := Cat(io.operand_b_i, "b0".U(1.W))
  }

  // actual adder
  io.adder_result_ext_o := adder_in_a + adder_in_b
  adder_result := io.adder_result_ext_o(32, 1).asUInt
  io.adder_result_o := adder_result

  ////////////////
  // Comparison //
  ////////////////

  var is_equal: Bool = Wire(Bool())
  var is_greater_equal: Bool = Wire(Bool()) // handles both signed and unsigned forms
  var cmp_signed: UInt = Wire(UInt(1.W))

  cmp_signed := MuxLookup(io.operator_i.asUInt(), false.B, Array(
    ALU_GE.asUInt() -> true.B,
    ALU_LT.asUInt() -> true.B,
    ALU_SLT.asUInt() -> true.B,
    // RV32B only
    ALU_MIN.asUInt() -> true.B,
    ALU_MAX.asUInt() -> true.B,
  ))

  is_equal := (adder_result === "b0".U(32.W))
  // io.is_equal_result_o := is_equal

  // Is greater equal
  when((io.operand_a_i(31) ^ io.operand_b_i(31)) === false.B) { // 如果a和b同号
    is_greater_equal := (adder_result(31) === false.B)
  }.otherwise {
    is_greater_equal := io.operand_a_i(31) ^ cmp_signed
  }


  val cmp_result: Bool = Wire(Bool())

  cmp_result := MuxLookup(io.operator_i.asUInt(), is_equal, Array(
    ALU_EQ.asUInt() -> is_equal,
    ALU_NE.asUInt() -> ~is_equal,
    ALU_GE.asUInt() -> is_greater_equal,
    ALU_GEU.asUInt() -> is_greater_equal,
    ALU_MAX.asUInt() -> is_greater_equal,
    ALU_MAXU.asUInt() -> is_greater_equal,
    ALU_LT.asUInt() -> ~is_greater_equal,
    ALU_LTU.asUInt() -> ~is_greater_equal,
    ALU_MIN.asUInt() -> ~is_greater_equal,
    ALU_MINU.asUInt() -> ~is_greater_equal,
    ALU_SLT.asUInt() -> ~is_greater_equal,
    ALU_SLTU.asUInt() -> ~is_greater_equal,
  ))

  io.comparison_result_o := cmp_result

  ///////////
  // Shift //
  ///////////
  var shift_result: UInt = Wire(UInt(32.W))
  val shift: UInt = io.operand_b_i(5, 0).asUInt()


  shift_result := MuxLookup(io.operator_i.asUInt(), 0.U, Array(
    ALU_SLL.asUInt() -> (io.operand_a_i << shift), // SLL: Shift Left Logical
    ALU_SRA.asUInt() -> (io.operand_a_i.asSInt() >> shift).asUInt(), // SRA: Shift Right Arithmetic
    ALU_SRL.asUInt() -> (io.operand_a_i >> shift) // SRL: Shift Right Logical
  ))


  ///////////////////
  // Bitwise Logic //
  ///////////////////
  var bwlogic_result: UInt = Wire(UInt(32.W))

  bwlogic_result := MuxLookup(io.operator_i.asUInt(), 0.U, Array(
    ALU_AND.asUInt() -> (io.operand_a_i & io.operand_b_i),
    ALU_OR.asUInt() -> (io.operand_a_i | io.operand_b_i),
    ALU_XOR.asUInt() -> (io.operand_a_i ^ io.operand_b_i)
  ))

  ////////////////
  // Result mux //
  ////////////////
  io.result_o := MuxLookup(io.operator_i.asUInt(), 0.U, Array(
    // Bitwise Logic Operations (negate: RV32B)
    ALU_XOR.asUInt() -> bwlogic_result,
    ALU_XNOR.asUInt() -> bwlogic_result,
    ALU_OR.asUInt() -> bwlogic_result,
    ALU_ORN.asUInt() -> bwlogic_result,
    ALU_AND.asUInt() -> bwlogic_result,
    ALU_ANDN.asUInt() -> bwlogic_result,

    // Adder Operations
    ALU_ADD.asUInt() -> adder_result,
    ALU_SUB.asUInt() -> adder_result,

    // Shift Operations
    ALU_SLL.asUInt() -> shift_result,
    ALU_SRL.asUInt() -> shift_result,
    ALU_SRA.asUInt() -> shift_result,

    // Comparison Operations
    ALU_EQ.asUInt() -> Cat("h0".U(31.W), cmp_result),
    ALU_NE.asUInt() -> Cat("h0".U(31.W), cmp_result),
    ALU_GE.asUInt() -> Cat("h0".U(31.W), cmp_result),
    ALU_GEU.asUInt() -> Cat("h0".U(31.W), cmp_result),
    ALU_LT.asUInt() -> Cat("h0".U(31.W), cmp_result),
    ALU_LTU.asUInt() -> Cat("h0".U(31.W), cmp_result),
    ALU_SLT.asUInt() -> Cat("h0".U(31.W), cmp_result),
    ALU_SLTU.asUInt() -> Cat("h0".U(31.W), cmp_result),
  ))

}
