package com.where.letsmeet.model

data class GroupChatModel(
    val groupId: String? = null,
    val users: HashMap<String, Boolean> = HashMap(),
    val comments : HashMap<String, ChatModel.Comment> = HashMap(),
    val title: String? = null,
    val groupImage: String? = null,
    val lastMessage: String? = null

){
    class Comment(val uid: String? = null, val message: String? = null, val time: String? = null)
}

