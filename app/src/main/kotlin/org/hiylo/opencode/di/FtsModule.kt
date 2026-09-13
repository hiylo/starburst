/*
 * Copyright(c) 2016 - Present, Clouds Studio Holding Limited. All rights reserved.
 * Project : opencode
 * File : FtsModule.kt
 * Date : 2026/09/13 00:00:00
 * Author : Hsi Chu
 * Contact : hiylo@live.com
 */
package org.hiylo.opencode.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.hiylo.opencode.data.search.MessageFtsIndex
import org.hiylo.opencode.data.search.SqliteMessageFtsIndex
import javax.inject.Singleton

/**
 * 消息全文索引的 Hilt 绑定：接口 [MessageFtsIndex] → SQLite 实现 [SqliteMessageFtsIndex]。
 *
 * @author Hsi Chu
 * @since V1.4.0
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FtsModule {

    @Binds
    @Singleton
    abstract fun bindMessageFtsIndex(impl: SqliteMessageFtsIndex): MessageFtsIndex
}
