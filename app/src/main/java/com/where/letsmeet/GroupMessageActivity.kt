package com.where.letsmeet

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.where.letsmeet.model.GroupChatModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_group_message.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class GroupMessageActivity : AppCompatActivity() {

    private val fireDatabase = FirebaseDatabase.getInstance().reference
    private var groupId: String? = null
    private var destinationUid : String? = null
    private var uid: String? = null
    private var recyclerView: RecyclerView? = null

    @SuppressLint("SimpleDateFormat")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_message)

        val imageView = findViewById<ImageView>(R.id.groupMessageActivity_ImageView)
        val editText = findViewById<TextView>(R.id.groupMessageActivity_editText)

        // 메세지를 보낸 시간
        val time = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("MM월dd일 hh:mm")
        val curTime = dateFormat.format(Date(time)).toString()

        groupId = intent.getStringExtra("groupId")
        uid = Firebase.auth.currentUser?.uid.toString()
        recyclerView = findViewById(R.id.groupMessageActivity_recyclerView)

        imageView.setOnClickListener {
            val groupChatModel = GroupChatModel()
            groupChatModel.users.put(uid.toString(), true)
            groupChatModel.users.put(destinationUid!!, true)

            val comment = GroupChatModel.Comment(uid, editText.text.toString(), curTime)
            fireDatabase.child("groupChats").child(groupId.toString()).push().setValue(comment)
            groupMessageActivity_editText.text = null
        }

        loadGroupChatMessages()
    }

    private fun loadGroupChatMessages() {
        recyclerView?.layoutManager = LinearLayoutManager(this@GroupMessageActivity)
        recyclerView?.adapter = GroupRecyclerViewAdapter()

        fireDatabase.child("groupChats").child(groupId.toString())
            .addValueEventListener(object : ValueEventListener {
                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    val groupChatList = ArrayList<GroupChatModel.Comment>()
                    for (data in snapshot.children) {
                        val comment = data.getValue(GroupChatModel.Comment::class.java)
                        comment?.let {
                            groupChatList.add(it)
                        }
                    }
                    (recyclerView?.adapter as? GroupRecyclerViewAdapter)?.setGroupChatList(groupChatList)
                    recyclerView?.scrollToPosition(groupChatList.size - 1)
                }
            })
    }

    inner class GroupRecyclerViewAdapter :
        RecyclerView.Adapter<GroupRecyclerViewAdapter.GroupMessageViewHolder>() {

        private val groupChatList = ArrayList<GroupChatModel.Comment>()

        fun setGroupChatList(list: List<GroupChatModel.Comment>) {
            groupChatList.clear()
            groupChatList.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupMessageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_group_message, parent, false)
            return GroupMessageViewHolder(view)
        }

        @SuppressLint("RtlHardcoded")
        override fun onBindViewHolder(holder: GroupMessageViewHolder, position: Int) {
            val comment = groupChatList[position]
            holder.textViewMessage.text = comment.message
            holder.textViewTime.text = comment.time

            if (comment.uid == uid) {
                // Sent by the current user
                holder.layoutDestination.visibility = View.INVISIBLE
                holder.layoutMain.gravity = Gravity.RIGHT
            } else {
                // Sent by other users
                holder.layoutDestination.visibility = View.VISIBLE
                holder.layoutMain.gravity = Gravity.LEFT
            }
        }

        override fun getItemCount(): Int {
            return groupChatList.size
        }

        inner class GroupMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textViewMessage: TextView = view.findViewById(R.id.groupMessageItem_textView_message)
            val textViewName: TextView = view.findViewById(R.id.groupMessageItem_textview_name)
            val imageViewProfile: ImageView = view.findViewById(R.id.groupMessageItem_imageview_profile)
            val layoutDestination: LinearLayout = view.findViewById(R.id.groupMessageItem_layout_destination)
            val layoutMain: LinearLayout = view.findViewById(R.id.groupMessageItem_linearlayout_main)
            val textViewTime : TextView = view.findViewById(R.id.groupMessageItem_textView_time)
        }
    }
}
