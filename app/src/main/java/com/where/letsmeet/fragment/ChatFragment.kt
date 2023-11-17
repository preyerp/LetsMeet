package com.where.letsmeet.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.where.letsmeet.MessageActivity
import com.where.letsmeet.R
import com.where.letsmeet.model.Friend
import com.where.letsmeet.model.GroupChatModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import com.where.letsmeet.GroupMessageActivity
import com.where.letsmeet.model.ChatModel
import java.util.*
import java.util.Collections.reverseOrder

class ChatFragment : Fragment() {
    companion object {
        fun newInstance(): ChatFragment {
            return ChatFragment()
        }
    }

    private val fireDatabase = FirebaseDatabase.getInstance().reference
    private lateinit var recyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var chatList: MutableList<ChatModel>
    private lateinit var groupId: String
    private lateinit var databaseRef: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var uid: String

    // 메모리에 올라갔을 때
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // 프레그먼트를 포함하고 있는 액티비티에 붙었을 때
    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    // 뷰가 생성되었을 때
    // 프레그먼트와 레이아웃을 연결시켜주는 부분
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        recyclerView = view.findViewById(R.id.chatfragment_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        chatList = mutableListOf()
        chatAdapter = ChatAdapter(chatList)
        recyclerView.adapter = chatAdapter

        auth = FirebaseAuth.getInstance()
        databaseRef = FirebaseDatabase.getInstance().reference
        uid = auth.currentUser?.uid.toString() // 현재 사용자의 uid 가져오기

        groupId = "your_group_id" // 단체 채팅방의 고유 식별자를 가져와야 함

        loadChatList()
        loadGroupChatMessages()

        return view
    }

    private fun loadChatList() {
        fireDatabase.child("chatrooms").orderByChild("users/$uid").equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    chatList.clear()
                    for (data in dataSnapshot.children) {
                        val chatModel = data.getValue<ChatModel>()
                        chatModel?.let {
                            chatList.add(it)
                        }
                    }
                    chatAdapter.notifyDataSetChanged()
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    // 오류 처리
                }
            })
    }

    private fun loadGroupChatMessages() {
        val groupChatRef = databaseRef.child("groupChats").child(groupId)

        groupChatRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.exists()) {
                    val groupChatModel = dataSnapshot.getValue(GroupChatModel::class.java)
                    groupChatModel?.let {
                        // 단체 채팅방의 메시지 목록을 처리
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // 오류 처리
            }
        })
    }

    inner class ChatAdapter(private val chatList: List<ChatModel>) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.chat_item_imageview)
            val textViewTitle: TextView = view.findViewById(R.id.chat_textview_title)
            val textViewLastMessage: TextView = view.findViewById(R.id.chat_item_textview_lastmessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val chatModel = chatList[position]
            var destinationUid: String? = null

            // 채팅방에 있는 유저 모두 체크
            for (user in chatModel.users.keys) {
                if (!user.equals(uid)) {
                    destinationUid = user
                }
            }

            fireDatabase.child("users").child("$destinationUid").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val friend = snapshot.getValue<Friend>()
                    Glide.with(holder.itemView.context).load(friend?.profileImageUrl)
                        .apply(RequestOptions().circleCrop())
                        .into(holder.imageView)
                    holder.textViewTitle.text = friend?.name
                }

                override fun onCancelled(error: DatabaseError) {
                    // 오류 처리
                }
            })

            // 메시지 내림차순 정렬 후 마지막 메시지의 키값을 가져옴
            val commentMap = TreeMap<String, ChatModel.Comment>(reverseOrder())
            commentMap.putAll(chatModel.comments)
            val lastMessageKey = commentMap.keys.toTypedArray()[0]
            holder.textViewLastMessage.text = chatModel.comments[lastMessageKey]?.message

            // 채팅창 선택 시 이동
            holder.itemView.setOnClickListener {
                val intent = Intent(context, MessageActivity::class.java)
                intent.putExtra("destinationUid", destinationUid)
                context?.startActivity(intent)
            }
        }

        override fun getItemCount(): Int {
            return chatList.size
        }
    }

    inner class GroupChatAdapter(private val groupChatList: List<GroupChatModel>) : RecyclerView.Adapter<GroupChatAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.group_chat_item_imageview)
            val textViewTitle: TextView = view.findViewById(R.id.group_chat_textview_title)
            val textViewLastMessage: TextView = view.findViewById(R.id.group_chat_item_textview_lastmessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_group_message, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val groupChatModel = groupChatList[position]
            var destinationGroupId: String? = null

            // 단체 채팅방의 정보 설정
            holder.textViewTitle.text = groupChatModel.title
            holder.textViewLastMessage.text = groupChatModel.lastMessage

            // 단체 채팅방의 이미지 설정
            Glide.with(holder.itemView.context).load(groupChatModel.groupImage)
                .apply(RequestOptions().circleCrop())
                .into(holder.imageView)

            // 단체 채팅방 선택 시 이동
            holder.itemView.setOnClickListener {
                val intent = Intent(context, GroupMessageActivity::class.java)
                intent.putExtra("groupId", destinationGroupId)
                context?.startActivity(intent)
            }
        }

        override fun getItemCount(): Int {
            return groupChatList.size
        }
    }

}
