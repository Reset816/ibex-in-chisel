import chisel3._
import chisel3.util.Cat
import chisel3.util.experimental.loadMemoryFromFile
import firrtl.annotations.MemoryLoadFileType

class DataMem extends Module {
  val io = IO(new Bundle {
    val addr: UInt = Input(UInt(32.W)) // LSU 传给内存需要操作的地址
    val data_w_req: Bool = Input(Bool()) // LSU 请求内存写操作
    val data_w: UInt = Input(UInt(32.W)) // LSU传给内存需要写的数据
    val data_r_req: Bool = Input(Bool()) // LSU 请求内存读操作
    val data_r: UInt = Output(UInt(32.W)) // 内存返回给 LSU 的数据
    val read_data_vaild: Bool = Output(Bool()) // 内存返回的数据是否有效

  })

  val mem = Mem(128, UInt(8.W))
  loadMemoryFromFile(mem, "./src/test/MemoryofInstruction.txt", MemoryLoadFileType.Hex)

  ////////////////
  //    read    //
  ////////////////

  io.data_r := Cat(mem(io.addr), mem(io.addr + 1.U), mem(io.addr + 2.U), mem(io.addr + 3.U))
  io.read_data_vaild := Mux(io.data_r_req, true.B, false.B)

  ////////////////
  //    write   //
  ////////////////

  when(io.data_w_req) {
    mem(io.addr) := io.data_w(31, 24)
    mem(io.addr + 1.U) := io.data_w(23, 16)
    mem(io.addr + 2.U) := io.data_w(15, 8)
    mem(io.addr + 3.U) := io.data_w(7, 0)
  }

}

