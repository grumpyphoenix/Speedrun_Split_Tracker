package com.grumpy_phoenix.speedrun_split_tracker.domain

/** A folder in the library tree; [parentId] is null for top-level folders. */
data class Folder(val id: Long, val parentId: Long?, val name: String)

/** A segment list plus the runs done with it; [folderId] is null at the top level. */
data class RunFile(val id: Long, val folderId: Long?, val name: String)
