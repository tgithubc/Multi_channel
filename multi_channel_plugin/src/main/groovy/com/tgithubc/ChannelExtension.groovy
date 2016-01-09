package com.tgithubc

import com.android.builder.model.SigningConfig


class ChannelExtension {
    String name;
    SigningConfig signingConfig;
    List<String> childFlavors;

    ChannelExtension(String name) {
        this.name = name
    }
}
