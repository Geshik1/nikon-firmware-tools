package com.nikonhacker.dfr;

import com.nikonhacker.Format;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class CodeStructure {

    protected int entryPoint;

    // TODO : Optimize by merging instructions/labels/functions/returns/ends in a same table with various properties ?

    /** Map address -> Instruction */
    TreeMap<Integer, DisassembledInstruction> instructions = new TreeMap<Integer, DisassembledInstruction>();
    
    /** Map address -> Labels */
    Map<Integer, Symbol> labels = new TreeMap<Integer, Symbol>();
    
    /** Map address -> Functions */
    SortedMap<Integer, Function> functions = new TreeMap<Integer, Function>();

    /** Map address -> Start of corresponding function */
    Map<Integer, Integer> returns = new TreeMap<Integer, Integer>();

    /** Map address -> Start of corresponding function 
     *  (This Map may differ from returns due to delay slots)
     */
    Map<Integer, Integer> ends = new TreeMap<Integer, Integer>();

    public CodeStructure(int address) {
        this.entryPoint = address;
    }

    public int getEntryPoint() {
        return entryPoint;
    }

    public TreeMap<Integer, DisassembledInstruction> getInstructions() {
        return instructions;
    }

    
    public Map<Integer, Symbol> getLabels() {
        return labels;
    }

    private boolean isLabel(Integer address) {
        return labels.containsKey(address);
    }

    
    public SortedMap<Integer, Function> getFunctions() {
        return functions;
    }

    protected boolean isFunction(Integer address) {
        return functions.containsKey(address);
    }

    protected String getFunctionName(Integer address) {
        Symbol symbol = functions.get(address);
        return symbol==null?null:symbol.getName();
    }


    public Map<Integer, Integer> getReturns() {
        return returns;
    }

    protected boolean isReturn(Integer address) {
        return returns.containsKey(address);
    }


    public Map<Integer, Integer> getEnds() {
        return ends;
    }

    private boolean isEnd(Integer address) {
        return ends.containsKey(address);
    }





    public void writeDisassembly(Writer writer, Range memRange, Range fileRange, Set<OutputOption> outputOptions) throws IOException {

        // Start output
        Integer address = memRange.getStart();
        DisassembledInstruction instruction = instructions.get(address);

        int memoryFileOffset = outputOptions.contains(OutputOption.OFFSET)?(fileRange.start - fileRange.fileOffset):0;

        while (instruction != null && address < memRange.getEnd()) {
            writeInstruction(writer, address, instruction, memoryFileOffset, outputOptions);

            address = instructions.higherKey(address);
            instruction = address==null?null:instructions.get(address);
        }

    }

    public void writeInstruction(Writer writer, Integer address, DisassembledInstruction instruction, int memoryFileOffset, Set<OutputOption> outputOptions) throws IOException {
        // function
        if (isFunction(address)) {
            Function function = functions.get(address);
            writer.write("\n; ************************************************************************\n");
            writer.write("; " + function.getTitleLine() + "\n");
            writer.write("; ************************************************************************\n");
        }

        // label
        if (isLabel(address)) {
            writer.write(labels.get(address).getName() + ":\n");
        }

        if (EnumSet.of(OpCode.Type.JMP, OpCode.Type.BRA, OpCode.Type.CALL, OpCode.Type.INT).contains(instruction.opcode.type)) {
            try {
                int targetAddress;
                // get address in comment (if any) or in operand
                if (instruction.comment.length() > 0) {
                    targetAddress = Format.parseUnsigned(instruction.comment);
                }
                else {
                    targetAddress = Format.parseUnsigned(instruction.operands);
                }

                // fetch corresponding symbol
                Symbol symbol;
                if (EnumSet.of(OpCode.Type.JMP, OpCode.Type.BRA).contains(instruction.opcode.type)) {
                    symbol = labels.get(targetAddress);
                }
                else { // CALLs
                    symbol = functions.get(targetAddress);
                }

                // If found, replace target address by label
                String text = "";
                if (symbol != null) {
                    text = symbol.getName();
                }

                if (EnumSet.of(OpCode.Type.JMP, OpCode.Type.BRA).contains(instruction.opcode.type)) {
                    // Add (skip) or (loop) according to jump direction
                    //TODO only if(areInSameRange(address, targetAddress))
                    if (instruction.comment.length() > 0) {
                        instruction.comment = (text + " " + skipOrLoop(address, targetAddress)).trim();
                    }
                    else {
                        instruction.operands = text;
                        instruction.comment = skipOrLoop(address, targetAddress);
                    }
                }
                else { // CALL or INT
                    if (outputOptions.contains(OutputOption.PARAMETERS)) {
                        // Add function parameters
                        Function function = (Function)symbol;
                        if (function != null && function.getParameterList() != null) {
                            text +="(";
                            String prefix = "";
                            for (Symbol.Parameter parameter : function.getParameterList()) {
                                if (parameter.getInVariable() != null) {
                                    if(!text.endsWith("(")) {
                                        text+=", ";
                                    }
                                    text+=parameter.getInVariable() + "=";
                                    if (instruction.cpuState.isRegisterValid(parameter.getRegister())) {
                                        text+="0x" + Integer.toHexString(instruction.cpuState.getReg(parameter.getRegister()));
                                    }
                                    else {
                                        text+=CPUState.REG_LABEL[parameter.getRegister()];
                                    }
                                }
                                else if (parameter.getOutVariable() != null) {
                                    if (prefix.length() > 0) {
                                        prefix += ",";
                                    }
                                    prefix+=CPUState.REG_LABEL[parameter.getRegister()];
                                }
                            }
                            text += ")";
                            if (prefix.length() > 0) {
                                text = prefix + "=" + text;
                            }
                        }
                    }

                    if (instruction.comment.length() > 0) {
                        instruction.comment = text;
                    }
                    else {
                        instruction.operands = text;
                    }
                }
            } catch(ParsingException e){
                // noop
            }
        }

        // print instruction
        Dfr.printDisassembly(writer, instruction, address, memoryFileOffset, outputOptions);

        // after return from function
        if (isEnd(address)) {
            Integer matchingStart = ends.get(address);
            if (matchingStart == null) {
                writer.write("; end of an unidentified function (never called)\n");
            }
            else {
                writer.write("; end of " + getFunctionName(matchingStart) + "\n");
            }
            writer.write("; ------------------------------------------------------------------------\n\n");
        }
    }

    private String skipOrLoop(Integer address, int targetAddress) {
        long target = targetAddress & 0xFFFFFFFFL;
        long addr = address & 0xFFFFFFFFL;
        return target > addr ?"(skip)":"(loop)";
    }

    public Function findFunctionIncluding(int address) {
        for (Function function : functions.values()) {
            for (CodeSegment codeSegment : function.getCodeSegments()) {
                if (address >= codeSegment.getStart() && address <= codeSegment.getEnd()) {
                    return function;
                }
            }
        }
        return null;
    }
}