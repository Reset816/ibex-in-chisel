import chisel3._
import chisel3.util.{Cat, Fill, is, switch}
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

    val multdiv_operand_a_i: UInt = Input(UInt(33.W))
    val multdiv_operand_b_i: UInt = Input(UInt(33.W))

    val multdiv_sel_i: Bool = Input(Bool())

    val imd_val_q_i: Vec[UInt] = Input(Vec(2, UInt(32.W)))
    //    val imd_val_d_o: Vec[UInt] = Output(Vec(2, UInt(32.W)))
    //    val imd_val_we_o: UInt = Output(UInt(2.W))

    val adder_result_o: UInt = Output(UInt(32.W))
    val adder_result_ext_o: UInt = Output(UInt(34.W))

    val result_o: UInt = Output(UInt(32.W))
    val comparison_result_o: Bool = Output(Bool())
    val is_equal_result_o: Bool = Output(Bool())

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

  {
    adder_op_b_negate := false.B
    switch(io.operator_i) {
      // Adder OPs
      is(ALU_SUB) {
        adder_op_b_negate := "b1".U(1.W)
      }

      // Comparator OPs
      is(ALU_EQ) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_NE) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_GE) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_GEU) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_LT) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_LTU) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_SLT) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_SLTU) {
        adder_op_b_negate := "b1".U(1.W)
      }

      // Comparator OPs
      is(ALU_MIN) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_MINU) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_MAX) {
        adder_op_b_negate := "b1".U(1.W)
      }
      is(ALU_MAXU) {
        adder_op_b_negate := "b1".U(1.W)
      }
    }
  }

  // prepare operand a
  adder_in_a := Mux(io.multdiv_sel_i, io.multdiv_operand_a_i, Cat(io.operand_a_i.asUInt, "b1".U(1.W)))

  // prepare operand b
  operand_b_neg := Cat(io.operand_b_i, "b0".U(1.W)) ^ Fill(33, true.B) // Reference: https://stackoverflow.com/questions/56439589/how-to-duplicate-a-single-bit-to-a-uint-in-chisel-3
  when(true.B === io.multdiv_sel_i) {
    adder_in_b := io.multdiv_operand_b_i
  }.elsewhen(true.B === adder_op_b_negate) {
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

  {
    cmp_signed := false.B
    switch(io.operator_i) {
      is(ALU_GE) {
        cmp_signed := "b1".U(1.W)
      }
      is(ALU_LT) {
        cmp_signed := "b1".U(1.W)
      }
      is(ALU_SLT) {
        cmp_signed := "b1".U(1.W)
      }
      // RV32B only
      is(ALU_MIN) {
        cmp_signed := "b1".U(1.W)
      }
      is(ALU_MAX) {
        cmp_signed := "b1".U(1.W)
      }
    }
  }

  is_equal := (adder_result === "b0".U(32.W))
  io.is_equal_result_o := is_equal

  // Is greater equal
  when((io.operand_a_i(31) ^ io.operand_b_i(31)) === false.B) { // 如果a和b同号
    is_greater_equal := (adder_result(31) === false.B)
  }.otherwise {
    is_greater_equal := io.operand_a_i(31) ^ cmp_signed
  }


  val cmp_result: Bool = Wire(Bool())

  {
    cmp_result := is_equal
    switch(io.operator_i) {
      is(ALU_EQ) {
        cmp_result := is_equal
      }
      is(ALU_NE) {
        cmp_result := ~is_equal
      }
      is(ALU_GE) {
        cmp_result := is_greater_equal
      }
      is(ALU_GEU) {
        cmp_result := is_greater_equal
      }
      is(ALU_MAX) {
        cmp_result := is_greater_equal
      }
      is(ALU_MAXU) {
        cmp_result := is_greater_equal
      }
      is(ALU_LT) {
        cmp_result := ~is_greater_equal
      }
      is(ALU_LTU) {
        cmp_result := ~is_greater_equal
      }
      is(ALU_MIN) {
        cmp_result := ~is_greater_equal
      }
      is(ALU_MINU) {
        cmp_result := ~is_greater_equal
      }
      is(ALU_SLT) {
        cmp_result := ~is_greater_equal
      }
      is(ALU_SLTU) {
        cmp_result := ~is_greater_equal
      }
    }
  }

  io.comparison_result_o := cmp_result

  ///////////
  // Shift //
  ///////////


  ////////////////
  // Result mux //
  ////////////////

  {
    io.result_o := 0.U
    switch(io.operator_i) {
      // Bitwise Logic Operations (negate: RV32B)
      //      is(ALU_XOR) {
      //        io.result_o := bwlogic_result
      //      }
      //      is(ALU_XNOR) {
      //        io.result_o := bwlogic_result
      //      }
      //      is(ALU_OR) {
      //        io.result_o := bwlogic_result
      //      }
      //      is(ALU_ORN) {
      //        io.result_o := bwlogic_result
      //      }
      //      is(ALU_AND) {
      //        io.result_o := bwlogic_result
      //      }
      //      is(ALU_ANDN) {
      //        io.result_o := bwlogic_result
      //      }

      // Adder Operations
      is(ALU_ADD) {
        io.result_o := adder_result
      }
      is(ALU_SUB) {
        io.result_o := adder_result
      }

      //      // Shift Operations
      //      is(ALU_SLL) {
      //        io.result_o := shift_result
      //      }
      //      is(ALU_SRL) {
      //        io.result_o := shift_result
      //      }
      //      is(ALU_SRA) {
      //        io.result_o := shift_result
      //      }

      // Comparison Operations
      is(ALU_EQ) {
        io.result_o := Cat("h0".U(31.W), cmp_result)
      }
      is(ALU_NE) {
        io.result_o := Cat("h0".U(31.W), cmp_result)
      }
      is(ALU_GE) {
        io.result_o := Cat("h0".U(31.W), cmp_result)
      }
      is(ALU_GEU) {
        io.result_o := Cat("h0".U(31.W), cmp_result)
      }
      is(ALU_LT) {
        io.result_o := Cat("h0".U(31.W), cmp_result)
      }
      is(ALU_LTU) {
        io.result_o := Cat("h0".U(31.W), cmp_result)
      }
      is(ALU_SLT) {
        io.result_o := Cat("h0".U(31.W), cmp_result)
      }
      is(ALU_SLTU) {
        io.result_o := Cat("h0".U(31.W), cmp_result)
      }
    }
  }


}