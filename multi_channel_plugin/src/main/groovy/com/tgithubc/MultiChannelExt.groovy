package com.tgithubc

import com.android.builder.model.SigningConfig

// 环境配置
class MultiChannelExt {

    def version
    def regulation
    def jarsignerPath
    def zipalignPath
    SigningConfig signingConfig
    boolean debugLog

    MultiChannelExt() {
    }

    @Override
    String toString() {
        """\
        MultiChannelExt {
            version= ${version},
            regulation= ${regulation},
            debugLog= ${debugLog},
            jarsignerPath=${jarsignerPath},
            zipalignPath=${zipalignPath},
            signingConfig=${signingConfig}
        }
        """.stripMargin()
    }
}
