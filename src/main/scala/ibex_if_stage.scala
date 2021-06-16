// TODO: pc + 4

import chisel3._
import chisel3.util.{Cat, Fill, MuxLookup, is, switch}

class ibex_if_stage(
                   ) extends Module {
  val io = IO(new Bundle {
    val req_i: Bool = Input(Bool()) // instruction request control

    val boot_addr_i: UInt = Input(UInt(32.W)) // also used for mtvec

    // output of ID stage
    val instr_valid_id_o: Bool = Output(Bool()) // instr in IF-ID is valid
    val instr_rdata_id_o: UInt = Output(UInt(32.W)) // instr for ID stage
    val instr_rdata_alu_id_o: UInt = Output(UInt(32.W)) // replicated instr for ID stage

    val pc_if_o: UInt = Output(UInt(32.W))
    val pc_id_o: UInt = Output(UInt(32.W))

    // control signals
    val instr_valid_clear_i: Bool = Input(Bool()) // clear instr valid bit in IF-ID
    val pc_set_i: Bool = Input(Bool()) // set the PC to a new value
    val pc_mux_i: pc_sel_e.Type = Input(pc_sel_e()) // selector for PC multiplexer

    // jump and branch target
    val branch_target_ex_i: UInt = Input(UInt(32.W)) // branch/jump target address

    // pipeline stall
    val id_in_ready_i: Bool = Input(Bool()) // ID stage is ready for new instr
    // misc signals
    val if_busy_o: Bool = Output(Bool())
  })
  val instr_valid_id_d: Bool = Wire(Bool())
  val instr_valid_id_q: Bool = Wire(Bool())
  // instruction memory related signals
  val instruction_mem_busy: Bool = Wire(Bool())

  val fetch_addr_n: UInt = Wire(UInt(32.W))

  val fetch_valid: Bool = Wire(Bool())
  val fetch_rdata: UInt = Wire(UInt(32.W))
  val fetch_addr: UInt = Wire(UInt(32.W))

  val if_instr_valid: Bool = Wire(Bool())
  val if_instr_rdata: UInt = Wire(UInt(32.W))
  val if_instr_addr: UInt = Wire(UInt(32.W))

  val if_id_pipe_reg_we: Bool = Wire(Bool()) // IF-ID pipeline reg write enable

  val instr_out: UInt = Wire(UInt(32.W))

  // fetch address selection mux
  fetch_addr_n := MuxLookup(io.pc_mux_i.asUInt(), Cat(io.boot_addr_i(31, 8), "h80".U(8.W)), Array(
    pc_sel_e.PC_BOOT.asUInt() -> Cat(io.boot_addr_i(31, 8), "h80".U(8.W)),
    pc_sel_e.PC_JUMP.asUInt() -> io.branch_target_ex_i,
  ))


  val instruction_mem = Module(new InstructionMem)
  instruction_mem.io.PC <> fetch_addr_n
  instruction_mem.io.req_i <> io.req_i
  instruction_mem.io.fetch_ready_i <> io.id_in_ready_i

  fetch_addr := instruction_mem.io.fetch_addr_o
  fetch_valid := instruction_mem.io.fetch_valid_o
  instruction_mem_busy := instruction_mem.io.busy_o
  instr_out := instruction_mem.io.fetch_rdata_o
  fetch_rdata := instruction_mem.io.fetch_rdata_o
  io.pc_if_o := if_instr_addr
  io.if_busy_o := instruction_mem_busy

  // The ID stage becomes valid as soon as any instruction is registered in the ID stage flops.
  // Note that the current instruction is squashed by the incoming pc_set_i signal.
  // Valid is held until it is explicitly cleared (due to an instruction completing)

  // Avoid compilation error
  val tmp1: Bool = Wire(Bool())
  tmp1 := if_instr_valid & io.id_in_ready_i & ~io.pc_set_i
  val tmp2: Bool = Wire(Bool())
  tmp2 := instr_valid_id_q & ~io.instr_valid_clear_i
  instr_valid_id_d := tmp1 | tmp2
  if_id_pipe_reg_we := if_instr_valid & io.id_in_ready_i
  io.instr_valid_id_o := instr_valid_id_q

  val reset_n: AsyncReset = (!reset.asBool).asAsyncReset

  withReset(reset_n) {
    instr_valid_id_q := RegNext(0.U, init = instr_valid_id_d)
  }

  io.instr_rdata_id_o := 0.U
  io.instr_rdata_alu_id_o := 0.U
  io.pc_id_o := 0.U


  when(if_id_pipe_reg_we) { // Reference: https://stackoverflow.com/questions/62388061/how-to-define-output-reg-in-chisel-properly
    io.instr_rdata_id_o := RegNext(instr_out)
    // To reduce fan-out and help timing from the instr_rdata_id flops they are replicated.
    io.instr_rdata_alu_id_o := RegNext(instr_out)
    io.pc_id_o := RegNext(io.pc_if_o)
  }

  // No BranchPredictor
  if_instr_valid := fetch_valid
  if_instr_rdata := fetch_rdata
  if_instr_addr := fetch_addr

}

