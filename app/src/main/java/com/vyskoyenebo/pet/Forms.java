package com.vyskoyenebo.pet;

final class Forms {
    static final String[] IDS = {"default", "remielle", "xingjianya", "promeia", "yixuan"};
    static final String[] TITLES = {"Q版蕾米", "蕾米埃尔", "星见雅", "普罗米娅", "仪玄"};
    static final int[] SIZES = {96, 128, 192, 256, 384, 512};
    static final String[] SIZE_TITLES = {"迷你 · 96", "较小 · 128", "小号 · 192", "中号 · 256", "大号 · 384", "超大 · 512"};
    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }
    static boolean valid(String id) { return IDS[index(id)].equals(id); }
    static String directory(String id) { return "forms/" + ("default".equals(id) ? "qremi" : id) + "/"; }
    static int defaultSize(String id) { return "default".equals(id) ? 192 : 384; }
}
