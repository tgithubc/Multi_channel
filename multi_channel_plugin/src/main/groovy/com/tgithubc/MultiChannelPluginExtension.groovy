package com.tgithubc

import com.android.builder.model.SigningConfig

class MultiChannelPluginExtension {
    String prefix;
    String subfix;

    String jarsignerPath;
    String zipalignPath;

    SigningConfig defaultSigningConfig;

    MultiChannelPluginExtension() {
    }
}
