import chisel3._
import chisel3.util.{is, switch}

class ibex_decoder extends Module {
  val io = IO(new Bundle {
    val clk_i: UInt = Input(UInt(1.W))
    val rst_ni: UInt = Input(UInt(1.W))

    // to/from controller
    val illegal_insn_o: Bool = Output(Bool())
    val ebrk_insn_o: Bool = Output(Bool())
    val mret_insn_o: Bool = Output(Bool())

    val dret_insn_o: Bool = Output(Bool())
    val ecall_insn_o: Bool = Output(Bool())
    val wfi_insn_o: Bool = Output(Bool())
    val jump_set_o: Bool = Output(Bool())
    val branch_taken_i: Bool = Input(Bool())
    val icache_inval_o: Bool = Output(Bool())

    // from IF-ID pipeline register
    val instr_first_cycle_i: Bool = Input(Bool())
    val instr_rdata_i: UInt = Input(UInt(32.W))
    val instr_rdata_alu_i: UInt = Input(UInt(32.W))
    val illegal_c_insn_i: Bool = Input(Bool())

    // immediates
    val imm_a_mux_sel_o: imm_a_sel_e.Type = Output(imm_a_sel_e())
    val imm_b_mux_sel_o: imm_b_sel_e.Type = Output(imm_b_sel_e())
    val bt_a_mux_sel_o: op_a_sel_e.Type = Output(op_a_sel_e())
    val bt_b_mux_sel_o: imm_a_sel_e.Type = Output(imm_a_sel_e())

    val imm_i_type_o: UInt = Output(UInt(32.W))
    val imm_s_type_o: UInt = Output(UInt(32.W))
    val imm_b_type_o: UInt = Output(UInt(32.W))
    val imm_u_type_o: UInt = Output(UInt(32.W))
    val imm_j_type_o: UInt = Output(UInt(32.W))
    val zimm_rs1_type_o: UInt = Output(UInt(32.W))

    // register file
    val rf_wdata_sel_o: rf_wd_sel_e.Type = Output(rf_wd_sel_e())
    val rf_we_o: Bool = Output(Bool())
    val rf_raddr_a_o: UInt = Output(UInt(5.W))
    val rf_raddr_b_o: UInt = Output(UInt(5.W))
    val rf_waddr_o: UInt = Output(UInt(5.W))
    val rf_ren_a_o: Bool = Output(Bool())
    val rf_ren_b_o: Bool = Output(Bool())

    //ALU
    val alu_operator_o: alu_op_e.Type = Output(alu_op_e())
    val alu_op_a_mux_sel_o: op_a_sel_e.Type = Output(op_a_sel_e())
    val alu_op_b_mux_sel_o: op_a_sel_e.Type = Output(op_a_sel_e())

    // MULT & DIV

    // CSRs

    // LSU
    val data_req_o: Bool = Output(Bool())
    val data_we_o: Bool = Output(Bool())
    val data_type_o: UInt = Output(UInt(2.W))
    val data_sign_extension_o: Bool = Output(Bool())

    //jump/branches
    val jump_in_dec_o: Bool = Output(Bool())
    val branch_in_dec_o: Bool = Output(Bool())

  })
  var illeagal_insn: Bool = Wire(Bool())
  var illeagal_reg_rv32e: Bool = Wire(Bool())
  var csr_illeagal: Bool = Wire(Bool())
  var rf_we: Bool = Wire(Bool())

  var instr: UInt = Wire(UInt(32.W))
  var instr_alu = Wire(UInt(32.W))
  var unused_instr_alu = Wire(UInt(10.W))

  // Source/Destination register instruction index
  var instr_rs1: UInt = Wire(UInt(5.W))
  var instr_rs2: UInt = Wire(UInt(5.W))
  var instr_rs3: UInt = Wire(UInt(5.W))
  var instr_rd: UInt = Wire(UInt(5.W))

  var use_rs3_d: Bool = Wire(Bool())
  var use_rs3_q: Bool = Wire(Bool())
  var csr_op: csr_op_e.Type = Wire(csr_op_e())

  var opcode: opcode_e.Type = Wire(opcode_e())
  var opcode_alu: opcode_e.Type = Wire(opcode_e())

  instr := io.instr_rdata_i
  instr_alu := io.instr_rdata_alu_i
  // immediate extraction and sign extension
  //****
  // immediate for CSR manipulation (zero extended)
  //****
  //if(RV32B)

  // source registers
  instr_rs1 := instr(19, 15).asUInt()
  instr_rs2 := instr(24, 20).asUInt()
  instr_rs3 := instr(31, 27).asUInt()
  //io.rf_raddr_a_o := if(use_rs3_q &~ io.instr_first_cycle_i) instr_rs3 else instr_rs1
  io.rf_raddr_a_o:=Mux(use_rs3_q & ~ io.instr_first_cycle_i,instr_rs3,instr_rs1)
  io.rf_raddr_b_o:=instr_rs2
  // destination register
  instr_rd:=instr(11,7).asUInt()
  io.rf_waddr_o:=instr_rd

  ///////////////////////
  // CSR operand check //
  ///////////////////////
  //****

  {
    io.jump_in_dec_o:=0.U(1.W)
    io.jump_set_o:=0.U(1.W)
    io.branch_in_dec_o:=0.U(1.W)
    io.icache_inval_o:=0.U(1.W)

    io.rf_wdata_sel_o:=rf_wd_sel_e.RF_WD_EX
    rf_we:=0.U(1.W)
    io.rf_ren_a_o:=0.U(1.W)
    io.rf_ren_b_o:=0.U(1.W)

    io.data_we_o:=0.U(1.W)
    io.data_type_o:=0.U(2.W)
    io.data_sign_extension_o:=0.U(1.W)
    io.data_req_o:=0.U(1.W)

    illeagal_insn:=0.U(1.W)
    io.ebrk_insn_o:=0.U(1.W)
    io.mret_insn_o:=0.U(1.W)
    io.dret_insn_o:=0.U(1.W)
    io.ecall_insn_o:=0.U(1.W)
    io.wfi_insn_o:=0.U(1.W)
    //opcode:=opcode_e.Type(instr(6,0).asUInt())
    switch(opcode){
      ///////////
      // Jumps //
      ///////////
      is(opcode_e.OPCODE_JAL){
        io.jump_set_o:=1.U(1.W)
        if(io.instr_first_cycle_i){
          rf_we:=0.U(1.W)
          io.jump_set_o:=1.U(1.W)
        }
        else{
          rf_we:=1.U(1.W)
        }

      }
      is(opcode_e.OPCODE_JALR){
        if(io.instr_first_cycle_i){
          rf_we:=0.U(1.W)
          io.jump_set_o:=1.U(1.W)
        }
        else{
          rf_we:=1.U(1.W)
        }
        if(instr(14,12).asUInt())
          illeagal_insn:=1.U(1.W)
      }
      is(opcode_e.OPCODE_BRANCH){
        io.branch_in_dec_o:=1.U(1.W)
        switch(instr(14,12).asUInt()){
          is("b000".U(3.W)){}
          is("b001".U(3.W)){}
          is("b100".U(3.W)){}
          is("b101".U(3.W)){}
          is("b110".U(3.W)){}
          is("b111".U(3.W)){
            illeagal_insn:=0.U(1.W)
          }
          //default
        }
        io.rf_ren_a_o:=1.U(1.W)
        io.rf_ren_b_o:=1.U(1.W)
      }
      ////////////////
      // Load/store //
      ////////////////
      is(opcode_e.OPCODE_STORE){
        io.rf_ren_a_o:=1.U(1.W)
        io.rf_ren_b_o:=1.U(1.W)
        io.data_req_o:=1.U(1.W)
        io.data_we_o:=1.U(1.W)
        if(instr(14,14).asUInt()) {
          illeagal_insn:=1.U(1.W)
        }
        switch(instr(13,12).asUInt()){
          is("b00".U(2.W)){
            io.data_type_o:="b10".U(2.W)
          }
          is("b01".U(2.W)){
            io.data_type_o:="b01".U(2.W)
          }
          is("b10".U(2.W)){
            io.data_type_o:="b00".U(2.W)
          }
          //default
        }
      }

      is(opcode_e.OPCODE_LOAD){
        io.rf_ren_a_o:=1.U(1.W)
        io.data_req_o:=1.U(1.W)
        io.data_type_o:="b00".U(2.W)
        io.data_sign_extension_o:= ~instr(14,14).asUInt()
        switch(instr(13,12).asUInt()){
          is("b00".U(2.W)){
            io.data_type_o:="b10".U(2.W)
          }
          is("b01".U(2.W)){
            io.data_type_o:="b01".U(2.W)
          }
          is("b10".U(2.W)){
            io.data_type_o:="b00".U(2.W)
            if(instr(14,14).asUInt()){
              illeagal_insn:=1.U(1.W)
            }
          }
        }
      }

      /////////
      // ALU //
      /////////
      is(opcode_e.OPCODE_LUI){
        rf_we:=1.U(1.W)
      }
      is(opcode_e.OPCODE_AUIPC){
        rf_we:=1.U(1.W)
      }
      is(opcode_e.OPCODE_OP_IMM){
        io.rf_ren_a_o:=1.U(1.W)
        rf_we:=1.U(1.W)
        switch(instr(14,12).asUInt()){
          is("b000".U(3.W)){}
          is("b010".U(3.W)){}
          is("b011".U(3.W)){}
          is("b100".U(3.W)){}
          is("b110".U(3.W)){}
          is("b111".U(3.W)){
            illeagal_insn:=0.U(1.W)
          }
          is("b001".U(3.W)){
            switch(instr(31,27).asUInt()){
              is("b0_0000".U(5.W)){
                illeagal_insn:= Mux(instr(26,25).asBool(),0.U(1.W),1.U(1.W))
              }
              is("b0_0100".U(5.W)){}
              is("b0_1001".U(5.W)){}
              is("b0_0101".U(5.W)){}
              is("b0_1101".U(5.W)){
                //RV32B
              }
              is("b0_0001".U(5.W)){
                if(instr(26,26).asUInt()){
                  //RV32B
                }
                else
                  illeagal_insn:=1.U(1.W)
              }
              is("b0_1100".U(5.W)){
                switch(instr(26,20).asUInt()){
                  is("b000_0000".U(7.W)){}
                  is("b000_0001".U(7.W)){}
                  is("b000_0010".U(7.W)){}
                  is("b000_0100".U(7.W)){}
                  is("b000_0101".U(7.W)){
                    //RV32B
                  }
                  is("b001_0000".U(7.W)){}
                  is("b001_0010".U(7.W)){}
                  is("b001_1001".U(7.W)){}
                  is("b001_1010".U(7.W)){
                    //RV32B
                  }
                  //default
                }
              }
              //default
            }
          }
          is("b101".U(3.W)){
            if(instr(26,26).asUInt()){
              //RV32B
            }
            else {
              switch(instr(31,27).asUInt()){
                is("b0_0000".U(5.W)){}
                is("b0_1000".U(5.W)){
                  illeagal_insn:= Mux(instr(26,25).asBool(),0.U(1.W),1.U(1.W))
                }
                is("b0_0100".U(5.W)){}
                is("b0_1100".U(5.W)){}
                is("b0_1001".U(5.W)){
                  //RV32B
                }
                is("b0_1101".U(5.W)){
                  //RV32B
                }
                is("b0_0101".U(5.W)){
                  //RV32B
                }
                is("b0_0001".U(5.W)){
                  //RV32B
                }
                //default
              }
            }
          }
          //default
        }
      }

      is(opcode_e.OPCODE_OP){
        io.rf_ren_a_o:=1.U(1.W)
        io.rf_ren_b_o:=1.U(1.W)
        rf_we:=1.U(1.W)
        //RV32B  if()
        //switch()
        //default
      }
      /////////////
      // Special //
      /////////////
      is(opcode_e.OPCODE_MISC_MEM){
        switch(instr(14,12).asUInt()){
          is("b000".U(3.W)){
            rf_we:=0.U(1.W)
          }
          is("b000".U(3.W)){
            io.jump_in_dec_o:=1.U(1.W)
            rf_we:=0.U(1.W)
            if(io.instr_first_cycle_i){
              io.jump_set_o:=1.U(1.W)
              io.icache_inval_o:=1.U(1.W)
            }
          }
          //default
        }
      }
      is(opcode_e.OPCODE_SYSTEM){
        if(instr(14,12).asUInt()==="b000".U(3.W)){
          switch(instr(31,20).asUInt()){
            is("h000".U(12.W)){
              io.ecall_insn_o:=1.U(1.W)
            }
            is("h001".U(12.W)){
              io.ebrk_insn_o:=1.U(1.W)
            }
            is("h302".U(12.W)){
              io.mret_insn_o:=1.U(1.W)
            }
            is("h7b2".U(12.W)){
              io.dret_insn_o:=1.U(1.W)
            }
            is("h105".U(12.W)){
              io.wfi_insn_o:=1.U(1.W)
            }
            //default
          }
          // rs1 and rd must be 0
          if(){
            illeagal_insn:=1.U(1.W)
          }
          else{
            //csr
            io.rf_wdata_sel_o:=rf_wd_sel_e.RF_WD_CSR
            rf_we:=1.U(1.W)
            if(~instr(14,14).asUInt())
              io.rf_ren_a_o:=1.U(1.W)

            switch(instr(13,12).asUInt()){
              is("b01".U(2.W)){
                //csr
              }
              is("b10".U(2.W)){
                //csr
              }
              is("b11".U(2.W)){
                //csr
              }
              //default
            }
            illeagal_insn:=csr_illeagal
          }
        }
      }
      //default illegal_insn = 1'b1;
    }
    if(io.illegal_c_insn_i){
      illeagal_insn:=1.U(1.W)
    }

    if(illeagal_insn){
      rf_we:=0.U(1.W)
      io.data_req_o:=0.U(1.W)
      io.data_we_o:=0.U(1.W)
      io.jump_set_o:=0.U(1.W)
      io.jump_in_dec_o:=0.U(1.W)
      io.branch_in_dec_o:=0.U(1.W)
      //csr
    }

  }
  /////////////////////////////
  // Decoder for ALU control //
  /////////////////////////////
  {
    io.alu_operator_o:=alu_op_e.ALU_SLTU
    io.alu_op_a_mux_sel_o:=op_a_sel_e.OP_A_IMM
    io.alu_op_b_mux_sel_o:=op_b_sel_e.OP_B_IMM
    io.imm_a_mux_sel_o:=imm_a_sel_e.IMM_A_ZERO
    io.imm_b_mux_sel_o:=imm_b_sel_e.IMM_B_I
    //opcode_alu
    use_rs3_d:=0.U(1.W)
    //multicycle

    switch(opcode_alu){
      ///////////
      // Jumps //
      ///////////
      is(opcode_e.OPCODE_JAL){}
      is(opcode_e.OPCODE_JALR){}
      is(opcode_e.OPCODE_BRANCH){}

      ////////////////
      // Load/store //
      ////////////////
      is(opcode_e.OPCODE_STORE){}
      is(opcode_e.OPCODE_LOAD){}

      /////////
      // ALU //
      /////////
      is(opcode_e.OPCODE_LUI){}
      is(opcode_e.OPCODE_AUIPC){}
      is(opcode_e.OPCODE_OP_IMM){}
      is(opcode_e.OPCODE_OP){}
      /////////////
      // Special //
      /////////////
      is(opcode_e.OPCODE_MISC_MEM){}
      is(opcode_e.OPCODE_SYSTEM){}
    }
  }
}

