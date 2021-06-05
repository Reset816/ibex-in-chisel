import chisel3._
import chisel3.util.Cat
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType

class InstructionMem extends Module {
  val io = IO(new Bundle {
    val PC: UInt = Input(UInt(32.W))
    val req_i: UInt = Input(UInt(1.W))
    val fetch_ready_i: UInt = Input(UInt(1.W))

    //val inst: UInt = Output(UInt(32.W))
    val fetch_valid_o: UInt = Output(UInt(1.W))
    val fetch_rdata_o: UInt = Output(UInt(32.W))
    val fetch_addr_o: UInt = Output(UInt(32.W))
    val busy_o: UInt = Output(UInt(1.W))

  })
  //reg
  val mem = Mem(128, UInt(8.W))
  loadMemoryFromFile(mem, "./src/test/MemoryofInstruction.txt", MemoryLoadFileType.Hex)

  io.fetch_addr_o := io.PC

  io.fetch_rdata_o := Cat(mem(io.PC), mem(io.PC + 1.U), mem(io.PC + 2.U), mem(io.PC + 3.U))

  when((io.req_i & io.fetch_ready_i) === true.B) {
    io.fetch_valid_o := 1.U(1.W)
    io.busy_o := 0.U(1.W)
  }.otherwise {
    io.fetch_valid_o := 0.U(1.W)
    io.busy_o := 1.U(1.W)
  }
}

