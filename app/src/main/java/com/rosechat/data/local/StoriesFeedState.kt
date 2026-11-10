package com.rosechat.data.local

import com.rosechat.data.model.Story

// Живёт только в памяти процесса, специально не персистится — это просто мост между
// HomeScreen (там уже загружена лента историй через Flow) и StoryViewerScreen, чтобы не
// тащить весь список через аргументы Navigation Compose (там только примитивы/строки).
object StoriesFeedState {
    // Сгруппировано по автору, в том порядке, в котором показывается лента (свои — первыми).
    var groups: List<List<Story>> = emptyList()
}
