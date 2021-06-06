import chisel3._

class LSU extends Module {
  val io = IO(new Bundle {
    val addr_i: UInt = Input(UInt(32.W))
    val write_enable: Bool = Input(Bool()) // CPU 请求写入
    val data_w_i: UInt = Input(UInt(32.W)) // CPU 请求要写的数据
    val read_req: Bool = Input(Bool()) // CPU 请求读取
    val data_r_o: UInt = Output(UInt(32.W)) // LSU传给CPU需要读的数据

    val lsu_busy: Bool = Output(Bool()) // LSU 正在等待内存传回数据

    val data_vaild: Bool = Output(Bool()) // LSU 的数据是否有效


    val addr_o: UInt = Output(UInt(32.W)) // LSU 传给内存需要操作的地址
    val data_w_req: Bool = Output(Bool()) // LSU 请求内存写操作
    val data_w_o: UInt = Output(UInt(32.W)) // LSU传给内存需要写的数据
    val data_r_req: Bool = Output(Bool()) // LSU 请求内存读操作
    val data_r_i: UInt = Input(UInt(32.W)) // 内存返回给 LSU 的数据
    val read_data_vaild: Bool = Input(Bool()) // 内存返回的数据是否有效
  })
  //  val addr_o: UInt = Wire(UInt(32.W)) // LSU 传给内存需要操作的地址
  //  val data_w_req: Bool = Wire(Bool()) // LSU 请求内存写操作
  //  val data_w_o: UInt = Wire(UInt(32.W)) // LSU传给内存需要写的数据
  //  val data_r_req: Bool = Wire(Bool()) // LSU 请求内存读操作
  //  val data_r_o: UInt = Wire(UInt(32.W)) // 内存返回给 LSU 的数据
  //  val read_data_vaild: Bool = Wire(Bool()) // 内存返回的数据是否有效

  val data_mem: DataMem = Module(new DataMem)
  //  io.addr_i <> data_mem.io.addr
  //  io.write_enable <> data_mem.io.data_w_req
  //  io.data_w_i <> data_mem.io.data_w
  //  io.read_req <> data_mem.io.data_r_req
  //  io.data_r_i <> data_mem.io.data_r


  //    data_mem.io.addr := RegNext(io.addr_i)
  //    data_mem.io.data_w_req := RegNext(io.write_enable)
  //    data_mem.io.data_w := RegNext(io.data_w_i)
  io.addr_o := io.addr_i
  io.data_w_req := io.write_enable
  io.data_w_o := io.data_w_i

  io.data_r_req := io.read_req
  io.data_r_o := io.data_r_i
  io.data_vaild := io.read_data_vaild

  io.lsu_busy := io.data_r_req & ~io.read_data_vaild

}

