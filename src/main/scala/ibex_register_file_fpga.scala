import chisel3._

class ibex_register_file_fpga extends Module {
  val io = IO(new Bundle {
    //Clock and Reset
    val clk_i:UInt=Input(UInt(1.W))
    val rst_ni:UInt=Input(UInt(1.W))
    val test_en_i:UInt=Input(UInt(1.W))
    val dummy_instr_id_i:UInt=Input(UInt(1.W))

    //Read port R1
    val raddr_a_i:UInt=Input(UInt(5.W))
    val rdata_a_o:UInt=Output(UInt(32.W))
    //Read port R2
    val raddr_b_i:UInt=Input(UInt(5.W))
    val rdata_b_o:UInt=Output(UInt(32.W))
    //Write port W1
    val waddr_a_i:UInt=Input(UInt(5.W))
    val wdata_a_i:UInt=Input(UInt(32.W))
    val we_a_i:UInt=Input(UInt(1.W))
  })
    val ADDR_WIDTH:UInt=5.U(1.W)
    val NUM_WORDS:UInt=

    var mem[NUM_WORDS]=RegInit(0.U(32.W))
    var we=Wire(UInt(1.W))

    //async_read a
    rdata_a_o:=(io.raddr_a_i==0)?0:mem[io.raddr_a_i]
    //async_read a
    rdata_b_o:=(io.raddr_b_i==0)?0:mem[io.raddr_b_i]
    //we select
    we:=(io.waddr_a_i)?0:io.we_a_i

    if(we)
      mem[io.waddr_a_i]:=io.wdata_a_i
}

