import chisel3._
import chisel3.util.{Cat, Fill, MuxLookup, is, switch}

class ibex_register_file_fpga(
                               val RV32E: Bool = false.B,
                               val DataWidth: UInt = 32.U,
                               val DummyInstructions: Bool = false.B,
                             ) extends Module {
  val io = IO(new Bundle {
    val test_en_i: Bool = Input(Bool())
    val dummy_instr_id_i: Bool = Input(Bool())

    //Read port R1
    val raddr_a_i: UInt = Input(UInt(5.W))
    val rdata_a_o: UInt = Output(UInt(32.W))
    //Read port R2
    val raddr_b_i: UInt = Input(UInt(5.W))
    val rdata_b_o: UInt = Output(UInt(32.W))
    //Write port W1
    val waddr_a_i: UInt = Input(UInt(5.W))
    val wdata_a_i: UInt = Input(UInt(32.W))
    val we_a_i: Bool = Input(Bool())
  })

  val ADDR_WIDTH: UInt = 5.U
  val NUM_WORDS: UInt = 32.U

  val mem: Vec[UInt] = Reg(Vec(32, Bits(32.W)))
  val we = Wire(UInt(1.W))

  //async_read a
  io.rdata_a_o := Mux(io.raddr_a_i === 0.U, 0.U, mem(io.raddr_a_i))

  //async_read b
  io.rdata_b_o := Mux(io.raddr_b_i === 0.U, 0.U, mem(io.raddr_b_i))

  //we select
  we := Mux(io.waddr_a_i === 0.U, 0.U, io.we_a_i)
    when(we === true.B) {
      mem(io.waddr_a_i) := io.wdata_a_i
    }


  // Dummy instruction changes not relevant for FPGA implementation
  val unused_dummy_instr: Bool = Wire(Bool())
  unused_dummy_instr := io.dummy_instr_id_i
  // Test enable signal not used in FPGA implementation
  val unused_test_en: Bool = Wire(Bool())
  unused_test_en := io.test_en_i
}

