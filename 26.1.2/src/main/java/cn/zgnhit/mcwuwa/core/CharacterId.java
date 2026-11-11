package cn.zgnhit.mcwuwa.core;

import java.util.Arrays;

public enum CharacterId {
    CHISA(1508, "kumokiri", Element.HAVOC),
    JINHSI(1304, "ages_of_harvest", Element.SPECTRO),
    MORNYE(1209, "starfield_calibrator", Element.FUSION),
    CARTETHYIA(1409, "defiers_thorn", Element.AERO),
    CAMELLYA(1603, "red_spring", Element.HAVOC),
    YANGYANG_XUANLING(1610, "azure_oath", Element.HAVOC),
    AERO_ROVER(1406, "bloodpacts_pledge", Element.AERO),
    AEMEATH(1210, "everbright_polestar", Element.FUSION),
    HIYUKI(1108, "frostburn", Element.GLACIO),
    LYNAE(1509, "spectrum_blaster", Element.SPECTRO),
    YINLIN(1302, "stringmaster", Element.ELECTRO),
    PHROLOVA(1608, "lethean_elegy", Element.HAVOC),
    HSIN(1311, "blooming_jadehaven", Element.ELECTRO),
    QIUYUAN(1411, "emerald_sentence", Element.AERO),
    CIACCONA(1407, "woodland_aria", Element.AERO),
    SHOREKEEPER(1505,"stellar_symphony",Element.SPECTRO),
    SIGRIKA(1412,"solsworn_ciphers",Element.AERO),
    GALBRENA(1208,"lux_and_umbra",Element.FUSION),
    PHOEBE(1506,"luminous_hymn",Element.SPECTRO),
    SUISUI(1110,"firstlights_herald",Element.GLACIO),
    CANTARELLA(1607,"whispers_of_sirens",Element.HAVOC),
    // Append only: persisted character ordinals of existing saves must not move.
    CHANGLI(1205,"blazing_brilliance",Element.FUSION),
    CARLOTTA(1107,"the_last_dance",Element.GLACIO),
    IUNO(1410,"moongazers_sigil",Element.AERO),
    LUPA(1207,"wildfire_mark",Element.FUSION),
    DENIA(1211,"forged_dwarf_star",Element.FUSION),
    QINGXIAO(1413,"glint_of_clouds",Element.AERO),
    ZHEZHI(1105,"rime_draped_sprouts",Element.GLACIO),
    ZANI(1507,"blazing_justice",Element.SPECTRO),
    SUOMING(1312,"unspoken_rue",Element.ELECTRO),
    LUCILLA(1109,"frame_signature",Element.GLACIO),
    AUGUSTA(1306,"thunderflare_dominion",Element.ELECTRO);

    private final int sourceId;
    private final String weaponPath;
    private final Element element;
    CharacterId(int sourceId, String weaponPath, Element element) {
        this.sourceId = sourceId; this.weaponPath = weaponPath; this.element = element;
    }
    public int sourceId() { return sourceId; }
    public String weaponPath() { return weaponPath; }
    public String weaponId() { return "mc_wuwa:" + weaponPath; }
    public Element element() { return element; }
    public static CharacterId fromSourceId(int id) {
        return Arrays.stream(values()).filter(c -> c.sourceId == id).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown character: " + id));
    }
}
