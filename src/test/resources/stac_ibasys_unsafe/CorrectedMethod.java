public static void test$Modification(byte[] i, byte[] pcode) {
        byte[] $3 = new byte[1];
        boolean $2 = false;
        byte[] imagedata = null;
        boolean success = false;
        boolean state = false;
        try {
            System.out.println("Loading passcode");
            ScalrApplyTest b = new ScalrApplyTest();
            ScalrApplyTest.setup(i);
            BufferedImage p = b.testApply1();
            int r = p.getWidth();
            int h = p.getHeight();
            int[] imageDataBuff = p.getRGB(0, 0, r, h, ((int[]) (null)), 0, r);
            ByteBuffer byteBuffer = ByteBuffer.allocate(imageDataBuff.length * 4);
            IntBuffer intBuffer = byteBuffer.asIntBuffer();
            intBuffer.put(imageDataBuff);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(byteBuffer.array());
            baos.flush();
            baos.close();
            System.out.println("Image Done");
            ScalrApplyTest.tearDown();
            byte[] pcodetest = new byte[128];
            int csize = imageDataBuff.length / 128;
            int ii = 0;
            for (int i1 = 0; i1 < (csize * 128); i1 += csize) {
                pcodetest[ii] = ((byte) (imageDataBuff[i1] % 2));
                ++ii;
            }
            imagedata = pcodetest;
            state = true;
        } catch (Exception var15) {
            System.out.println("worker ended, error: " + var15.getMessage());
        }
        if (state) {
            success = true;
            for (int var16 = 0; (var16 < imagedata.length) && (var16 < i.length); var16 += 4) {
                if ((var16 < imagedata.length) && (var16 < pcode.length)) {
                    int var17 = Math.abs(imagedata[var16]);
                    int var18 = Math.abs(pcode[var16]);
                    boolean var19 = (var18 % 2) == (var17 % 2);
                    if (!var19) {
                        success = false;
                    } else {
                        $2 = false;
                    }
                    imagedata[var16] = ((byte) ((var19) ? 1 : 0));
                } else {
                    int var17 = Math.abs(imagedata[0]);
                    int var18 = Math.abs(pcode[0]);
                    boolean $1 = (var18 % 2) == (var17 % 2);
                    if ($1) {
                        $2 = false;
                    } else {
                        $2 = false;
                    }
                    $3[0] = ((byte) (($1) ? 1 : 0));
                }
            }
            System.out.println(" - status:" + success);
        } else {
            success = false;
        }
}