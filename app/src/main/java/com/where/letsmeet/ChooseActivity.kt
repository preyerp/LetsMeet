package com.where.letsmeet

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.where.letsmeet.databinding.ActivitySubwayBinding
import com.where.letsmeet.databinding.ChooseRecyclerItemBinding
import com.where.letsmeet.databinding.MenuRecyclerItemBinding
import com.where.letsmeet.databinding.ActivityChooseBinding

class ChooseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChooseBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChooseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lists = intent.getStringArrayListExtra("출발지")!!
        var meetPurpose = intent.getStringExtra("목적")!!
        System.out.println("lists : $lists")
        binding.startRecyclerView.adapter = StartAdapter(this, lists, meetPurpose)
        binding.startRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.backButton.setOnClickListener {
            finish()
        }

    }

    class StartAdapter(private val context: Context, private val stationList: List<String>, private val purpose: String) : RecyclerView.Adapter<StartAdapter.ViewHolder>() {

        inner class ViewHolder(private val binding: ChooseRecyclerItemBinding) : RecyclerView.ViewHolder(binding.root){
            fun bind(stationName: String) {
                binding.startStationTitle.text = stationName
                binding.goButton.setOnClickListener {
                    val intent = Intent(context, SubwayActivity::class.java)
                    intent.putExtra("목적", purpose)
                    intent.putExtra("출발지 리스트", ArrayList<String>(stationList))
                    intent.putExtra("출발지", stationName)
                    context.startActivity(intent)


                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ChooseRecyclerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun getItemCount() = stationList.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(stationList[position])
            System.out.println("position : $position")
            System.out.println("stationList[position] : ${stationList[position]}")
            System.out.println("stationList : $stationList")
            System.out.println("stationList.size : ${stationList.size}")

        }


    }
}