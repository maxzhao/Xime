package com.kingzcheung.xime.settings

/**
 * Xime-managed third-party Chinese lexicons.
 *
 * Source URLs are pinned to immutable Git commits so an installed app always converts the
 * reviewed dataset version. The manager keeps only pure Han words when materializing a lexicon.
 */
data class ExtensionDictionaryDefinition(
    val id: String,
    val group: String,
    val name: String,
    val description: String,
    val sourceUrl: String,
    val sourcePage: String,
    val license: String,
    val licenseUrl: String,
    val approximateEntries: String,
    val format: ExtensionDictionaryFormat,
)

enum class ExtensionDictionaryFormat {
    RIME_DICTIONARY,
    SPACE_FREQUENCY,
    TAB_FREQUENCY,
    WORD_ONLY,
}

object ExtensionDictionaryCatalog {
    private const val RIME_ICE_REV = "158d8a218b974383336d4e2e7c919affc570d410"
    private const val WANGXIANG_REV = "0187cd4aeccaee24d6d462998c2b896efdfee988"
    private const val JIEBA_REV = "67fa2e36e72f69d9134b8a1037b83fbb070b9775"
    private const val THUOCL_REV = "a30ce79d895d01ab5132a5c74c29703ff7efb4cc"
    private const val NAMES_REV = "47d4af8d816f6212787ddfc49173cac3b994b58d"

    private const val RIME_ICE_PAGE = "https://github.com/iDvel/rime-ice"
    private const val RIME_ICE_LICENSE = "https://github.com/iDvel/rime-ice/blob/main/LICENSE"
    private const val WANGXIANG_PAGE = "https://github.com/amzxyz/RIME-LMDG"
    private const val WANGXIANG_LICENSE = "https://github.com/amzxyz/RIME-LMDG/blob/wanxiang/LICENSE"
    private const val JIEBA_PAGE = "https://github.com/fxsjy/jieba"
    private const val JIEBA_LICENSE = "https://github.com/fxsjy/jieba/blob/master/LICENSE"
    private const val THUOCL_PAGE = "https://github.com/thunlp/THUOCL"
    private const val THUOCL_LICENSE = "https://github.com/thunlp/THUOCL#开源协议"
    private const val NAMES_PAGE = "https://github.com/wainshine/Chinese-Names-Corpus"
    private const val NAMES_LICENSE = "https://github.com/wainshine/Chinese-Names-Corpus/blob/master/LICENSE"

    private fun rimeIce(
        id: String,
        name: String,
        description: String,
        file: String,
        approximateEntries: String,
    ) = ExtensionDictionaryDefinition(
        id = id,
        group = "通用词库",
        name = name,
        description = description,
        sourceUrl = "https://raw.githubusercontent.com/iDvel/rime-ice/$RIME_ICE_REV/cn_dicts/$file",
        sourcePage = RIME_ICE_PAGE,
        license = "GPL-3.0",
        licenseUrl = RIME_ICE_LICENSE,
        approximateEntries = approximateEntries,
        format = ExtensionDictionaryFormat.RIME_DICTIONARY,
    )

    private fun wangxiang(
        id: String,
        group: String,
        name: String,
        description: String,
        file: String,
        approximateEntries: String,
    ) = ExtensionDictionaryDefinition(
        id = id,
        group = group,
        name = name,
        description = description,
        sourceUrl = "https://raw.githubusercontent.com/amzxyz/RIME-LMDG/$WANGXIANG_REV/dicts/$file",
        sourcePage = WANGXIANG_PAGE,
        license = "CC BY 4.0",
        licenseUrl = WANGXIANG_LICENSE,
        approximateEntries = approximateEntries,
        format = ExtensionDictionaryFormat.RIME_DICTIONARY,
    )

    private fun thuocl(
        id: String,
        name: String,
        description: String,
        file: String,
        approximateEntries: String,
    ) = ExtensionDictionaryDefinition(
        id = id,
        group = "THUOCL 分类词库",
        name = name,
        description = description,
        sourceUrl = "https://raw.githubusercontent.com/thunlp/THUOCL/$THUOCL_REV/data/$file",
        sourcePage = THUOCL_PAGE,
        license = "THUOCL 开放协议",
        licenseUrl = THUOCL_LICENSE,
        approximateEntries = approximateEntries,
        format = ExtensionDictionaryFormat.TAB_FREQUENCY,
    )

    private fun namesCorpus(
        id: String,
        name: String,
        description: String,
        path: String,
        approximateEntries: String,
    ) = ExtensionDictionaryDefinition(
        id = id,
        group = "中文人名与成语语料",
        name = name,
        description = description,
        sourceUrl = "https://raw.githubusercontent.com/wainshine/Chinese-Names-Corpus/$NAMES_REV/$path",
        sourcePage = NAMES_PAGE,
        license = "Apache-2.0",
        licenseUrl = NAMES_LICENSE,
        approximateEntries = approximateEntries,
        format = ExtensionDictionaryFormat.WORD_ONLY,
    )

    val dictionaries: List<ExtensionDictionaryDefinition> = listOf(
        rimeIce("rime_ice_base", "雾凇基础词库", "长期维护的两字词与常用词频", "base.dict.yaml", "约 54.2 万"),
        rimeIce("rime_ice_ext", "雾凇扩展词库", "补充扩展词及多音字词", "ext.dict.yaml", "约 33.4 万"),
        rimeIce("rime_ice_tencent", "雾凇腾讯大词库", "覆盖广泛的大型通用词库", "tencent.dict.yaml", "约 98.1 万"),
        wangxiang("wanxiang_base", "通用词库", "万象基础词库", "现代词频与读音校对词库", "jichu.dict.yaml", "源文件约 45 MB"),
        wangxiang("wanxiang_long_phrase", "通用词库", "万象联想长词库", "以五字以上特殊长词和短语为主", "lianxiang.dict.yaml", "源文件约 7.8 MB"),
        ExtensionDictionaryDefinition(
            id = "jieba_general",
            group = "通用词库",
            name = "Jieba 通用词库",
            description = "轻量通用分词词典，包含词频",
            sourceUrl = "https://raw.githubusercontent.com/fxsjy/jieba/$JIEBA_REV/jieba/dict.txt",
            sourcePage = JIEBA_PAGE,
            license = "MIT",
            licenseUrl = JIEBA_LICENSE,
            approximateEntries = "约 33.7 万多字词",
            format = ExtensionDictionaryFormat.SPACE_FREQUENCY,
        ),

        wangxiang("wanxiang_poetry", "万象分类词库", "诗词", "诗词、古文与名句", "shici.dict.yaml", "源文件约 16.4 MB"),
        wangxiang("wanxiang_places", "万象分类词库", "地名", "国内地区与常见地名", "diming.dict.yaml", "源文件约 3.1 MB"),
        wangxiang("wanxiang_species", "万象分类词库", "动植物与物种", "动物、植物及物种名称", "wuzhong.dict.yaml", "源文件约 2.0 MB"),
        wangxiang("wanxiang_chemistry", "万象分类词库", "化学", "化学专业词汇", "huaxue.dict.yaml", "源文件约 0.79 MB"),
        wangxiang("wanxiang_medicine", "万象分类词库", "医学", "医学专业词汇", "yixue.dict.yaml", "源文件约 0.83 MB"),
        wangxiang("wanxiang_drugs", "万象分类词库", "药品", "药品及相关名称", "yaopin.dict.yaml", "源文件约 0.57 MB"),
        wangxiang("wanxiang_artists", "万象分类词库", "艺人姓名", "艺人姓名词表", "yiren.dict.yaml", "源文件约 0.36 MB"),
        wangxiang("wanxiang_celebrities", "万象分类词库", "名人姓名", "名人姓名词表", "mingren.dict.yaml", "源文件约 0.55 MB"),
        wangxiang("wanxiang_names", "万象分类词库", "常见人名", "高频常见中文人名", "renming.dict.yaml", "源文件约 0.56 MB"),

        thuocl("thuocl_it", "IT", "信息技术与软件开发词汇", "THUOCL_IT.txt", "约 1.6 万"),
        thuocl("thuocl_finance", "财经", "财经金融领域词汇", "THUOCL_caijing.txt", "约 3830"),
        thuocl("thuocl_idioms", "成语", "常用成语词汇", "THUOCL_chengyu.txt", "约 8519"),
        thuocl("thuocl_places", "地名", "国家、地区及行政区地名", "THUOCL_diming.txt", "约 4.48 万"),
        thuocl("thuocl_historical_people", "历史名人", "历史人物姓名", "THUOCL_lishimingren.txt", "约 1.37 万"),
        thuocl("thuocl_poetry", "诗词名句", "诗词与经典名句", "THUOCL_poem.txt", "约 1.37 万"),
        thuocl("thuocl_medicine", "医学", "医学与健康领域词汇", "THUOCL_medical.txt", "约 1.87 万"),
        thuocl("thuocl_food", "饮食", "食材、菜品与餐饮词汇", "THUOCL_food.txt", "约 8974"),
        thuocl("thuocl_law", "法律", "法律与司法领域词汇", "THUOCL_law.txt", "约 9896"),
        thuocl("thuocl_car", "汽车", "汽车品牌、车型及零部件", "THUOCL_car.txt", "约 1752"),
        thuocl("thuocl_animals", "动物", "动物及相关名称", "THUOCL_animal.txt", "约 1.73 万"),

        namesCorpus(
            "names_common",
            "常见中文人名",
            "大规模现代中文姓名，可能明显影响普通候选排序",
            "Chinese_Names_Corpus/Chinese_Names_Corpus%EF%BC%88120W%EF%BC%89.txt",
            "约 120 万",
        ),
        namesCorpus(
            "names_ancient",
            "古代人名",
            "中国古代人物姓名",
            "Chinese_Names_Corpus/Ancient_Names_Corpus%EF%BC%8825W%EF%BC%89.txt",
            "约 25 万",
        ),
        namesCorpus(
            "names_idioms",
            "成语语料",
            "多来源汇总的成语词表",
            "Chinese_Dict_Corpus/ChengYu_Corpus%EF%BC%885W%EF%BC%89.txt",
            "约 5 万",
        ),
    )

    val byId: Map<String, ExtensionDictionaryDefinition> = dictionaries.associateBy { it.id }
}
