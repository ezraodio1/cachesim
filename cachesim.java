import java.util.*;
import java.io.*;

class Block {
    
   int blockTag;
   boolean isValid;
   boolean isDirty;
   byte[] data;
   int address;

    public Block() {
        blockTag = 0;
        isValid = false;
        isDirty = false;
    }

    public Block(int bsize, int decaddress, int setcount, byte[] d) {
        data = d;
        address = decaddress;
        isValid = true;
        isDirty = true;
        blockTag = address / (setcount * bsize);
    }
}

public class cachesim {

    private File fileName;
    private int cacheSize;
    private int associativity;
    private String cacheType;
    private int blockSize;
    private Block[][] cacheArray;
    private byte[] mainMemory;
    private int numSets;

    public cachesim(File tracefile, int csize, int associ, String ctype, int bsize) {
        fileName = tracefile;
        cacheSize = csize;
        associativity = associ;
        cacheType = ctype;
        blockSize = bsize;

        mainMemory = new byte[(int) Math.pow(2, 16)];

        numSets = cacheSize / (blockSize * associativity);

        cacheArray = new Block[numSets][associativity];
        for (int i = 0; i < numSets; i++) {
            for (int j = 0; j < associativity; j++) {
                Block newBlock = new Block();
                cacheArray[i][j] = newBlock;
            }
        }
    }

    public static void main (String[] args) throws FileNotFoundException {
        File filename = new File(args[0]);
        int cache_size = Integer.parseInt(args[1]) * 1024;
        int assoc = Integer.parseInt(args[2]);
        String cache_type = args[3];
        int block_size = Integer.parseInt(args[4]);
        Scanner scanner = new Scanner(filename);

        cachesim sim = new cachesim(filename, cache_size, assoc, cache_type, block_size);
        
        while(scanner.hasNextLine()) {
            String[] input = scanner.nextLine().split(" ");

            String accessHexAddress = input[1];
            int accessDecAddress = Integer.parseInt(accessHexAddress, 16);
            int accessSetIndex = (accessDecAddress / block_size) % sim.numSets;
            int accessTag = accessDecAddress / (block_size * sim.numSets);
            int accessOffset = accessDecAddress % block_size;
            int accessSize = Integer.parseInt(input[2]);

            boolean hit = false;
            for (int i = 0; i < sim.cacheArray[accessSetIndex].length; i++) {
                if (sim.cacheArray[accessSetIndex][i].blockTag == accessTag && sim.cacheArray[accessSetIndex][i].isValid) {
                    hit = true;
                }
            }
            
            if (hit) {
                if (input[0].equals("load")) {
                    sim.loadHit(accessSetIndex, accessTag, accessSize, accessOffset, accessHexAddress);
                } 
                else {
                    String accessHexData = input[3];
                    byte[] accessData = hexToByteArray(accessHexData);
                    sim.storeHit(accessDecAddress, accessData, accessSetIndex, accessTag, accessOffset, accessHexAddress);
                } 
            }
            else {
                if (input[0].equals("load")) {
                    sim.loadMiss(accessDecAddress, accessSetIndex, accessOffset, accessSize, accessHexAddress);
                }
                else {
                    String accessHexData = input[3];
                    byte[] accessData = hexToByteArray(accessHexData);
                    if (sim.cacheType.equals("wt")) sim.storeMissWT(accessData, accessDecAddress, accessHexAddress);
                    else sim.storeMissWB(accessDecAddress, accessOffset, accessData, accessSetIndex, accessHexAddress);
                }
            }

        }
        scanner.close();
        
    }

    public void storeMissWB(int decAddress, int offset, byte[] da, int index, String hexAddress) {
        int k = 0;
        byte[] newData = new byte[blockSize];

        for (int i = decAddress - offset; i < decAddress - offset + blockSize; i++) {
            newData[k] = mainMemory[i];
            k++;
        }

        for (int i = 0; i < da.length; i++) {
            newData[offset + i] = da[i];
        }

        Block newBlock = new Block(blockSize, decAddress, numSets, da);

        boolean isFull = true;
        for (int i = 0; i < cacheArray[index].length; i++) {
            if (!cacheArray[index][i].isValid) {
                isFull = false;
                cacheArray[index][i] = newBlock;
                break;
            }
        }

        if (isFull) {
            if (cacheArray[index][0].isDirty) {
                for (int i = 0; i < cacheArray[index][0].data.length; i++) {
                    mainMemory[cacheArray[index][0].address + i] = cacheArray[index][0].data[i];
                }
            }

            for (int i = 0; i < cacheArray[index].length - 1; i++) {
                cacheArray[index][i] = cacheArray[index][i + 1];
            }
            cacheArray[index][cacheArray[index].length - 1] = newBlock;
        }

        System.out.println("store " + hexAddress + " miss");
    }

    public void loadMiss(int decAddress, int index, int offset, int len, String hexAddress) {
        byte[] memData = new byte[blockSize];
        for (int i = 0; i < blockSize; i++) {
            memData[i] = mainMemory[decAddress + i];
        }

        Block newBlock = new Block(blockSize, decAddress, numSets, memData);
        newBlock.isDirty = false;

        boolean fullCache = true;
        for(int i = 0; i < cacheArray[index].length; i++) {
            if (!cacheArray[index][i].isValid) {
                fullCache = false;
                cacheArray[index][i] = newBlock;
                break;
            }
        }

        if (fullCache) {
            if (cacheArray[index][0].isDirty) {
                for (int i = 0; i < cacheArray[index][0].data.length; i++) {
                    mainMemory[cacheArray[index][0].address + i] = cacheArray[index][0].data[i];
                }
            }

            for (int i = 0; i < cacheArray[index].length - 1; i++) {
                cacheArray[index][i] = cacheArray[index][i + 1];
            }
            cacheArray[index][cacheArray[index].length - 1] = newBlock;
        }

        String printme = " ";
        for (int i = 0; i < len; i++) {
            byte b = newBlock.data[i]; //add offset
            String st = String.format("%02X", b);
            printme = printme + st;
        }
        System.out.println("load " + hexAddress + " miss" + printme.toLowerCase());

    }

    public void loadHit(int index, int tag, int len, int offset, String hexAddress) {
        String printme = " ";
        int way = 0;
        for (int i = 0; i < cacheArray[index].length; i++) {
            if (cacheArray[index][i].blockTag == tag && cacheArray[index][i].isValid) {
                way = i;
                for (int j = 0; j < len; j++) {
                    byte b = cacheArray[index][i].data[j + offset]; 
                    String st = String.format("%02X", b);
                    printme = printme + st;
                }
                System.out.println("load " + hexAddress + " hit" + printme.toLowerCase());
                break;
            }
        }

        boolean full = true;
        Block copyBlock = cacheArray[index][way];
        int end = cacheArray[index].length - 1;
        for (int i = way; i < cacheArray[index].length; i++) {
            if (!cacheArray[index][i].isValid) {
                end = i;
                full = false;
                break;
            }
        }

        if (!full && end > 1) {
            for (int i = way; i < end - 1; i++) {
                cacheArray[index][i] = cacheArray[index][i + 1];
            }
            cacheArray[index][end - 1] = copyBlock;
        }

        else if (full) {
            for (int i = way; i < end; i++) {
                cacheArray[index][i] = cacheArray[index][i + 1];
            }
            cacheArray[index][end] = copyBlock;
        }
    }

    public void storeHit(int decAddress, byte[] da, int index, int tag, int offset, String hexAddress){
        if (cacheType.equals("wt")) {
            for (int i = 0; i < da.length; i++) {
                mainMemory[decAddress + i] = da[i];
            }
        }
        for (int i = 0; i < cacheArray[index].length; i++) {
            if (cacheArray[index][i].blockTag == tag && cacheArray[index][i].isValid) {
                int way = i;
                for (int j = 0; j < da.length; j++) {
                    cacheArray[index][i].data[offset + j] = da[j]; 
                }
                if (cacheType.equals("wb")) {
                    cacheArray[index][i].isDirty = true;
                }
                break;
            }
        }
        System.out.println("store " + hexAddress + " hit");
    }

    public void storeMissWT(byte[] da, int decAddress, String hexAddress) {
        for (int i = 0; i < da.length; i++) {
            mainMemory[decAddress + i] = da[i];
        }
        System.out.println("store " + hexAddress + " miss");
    }

    public static byte[] hexToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

}
