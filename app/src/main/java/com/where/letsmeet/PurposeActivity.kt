package com.where.letsmeet

import android.content.ContentValues
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.where.letsmeet.R
import com.where.letsmeet.databinding.ActivityPurposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class PurposeActivity : AppCompatActivity() {


    private val stations = mutableListOf<String>()

    private var startStations = mutableListOf<String>()

    lateinit var binding: ActivityPurposeBinding
    private var purpose = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurposeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fetchSubwayData()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            stations
        )

        binding.subwayStationAutocomplete.setAdapter(adapter)

        binding.choicePurpose.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButRestaurant -> purpose = "restaurant"
                R.id.radButCafe -> purpose = "cafe"
            }
        }

        binding.addStationButt.setOnClickListener {
            val enteredText = binding.subwayStationAutocomplete.text.toString()
            if (stations.contains(enteredText)) {
                if (!startStations.contains(enteredText)) {
                    startStations.add(enteredText)
                } else {
                    Toast.makeText(this, "이미 추가된 출발지 입니다.", Toast.LENGTH_SHORT).show()
                }
                binding.stationListText.text = startStations.toString()
                System.out.println("startStations : $startStations")
            } else {
                Toast.makeText(this, "역 이름을 다시 한번 확인해주세요.", Toast.LENGTH_SHORT).show()
            }

        }

        binding.subwayButton.setOnClickListener {
            if(startStations.size >= 2 && purpose != ""){
                val intent = Intent(this,ChooseActivity::class.java)
                intent.putExtra("목적", purpose)
                intent.putExtra("출발지", ArrayList<String>(startStations))
                startActivity(intent)
            } else{
                if(startStations.size < 2 && purpose != ""){
                    Toast.makeText(this, "출발지를 두개이상 추가해주세요", Toast.LENGTH_SHORT).show()
                } else if (startStations.size >= 2 && purpose == ""){
                    Toast.makeText(this, "만남 목적을 설정해주세요", Toast.LENGTH_SHORT).show()
                } else{
                    Toast.makeText(this, "만남의 목적을 선택하시고 출발지를 두개 이상 설정해주세요", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }
    private fun fetchSubwayData() {
        val db = FirebaseFirestore.getInstance()
        db.collection("finalSubway")    // 컬렉션 이름 변경
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val name = document.getString("name") ?: continue

                    stations.add(name)
                }
                System.out.println("stations : $stations")
            }
            .addOnFailureListener { exception ->
                System.out.println("에러")
                Log.w(ContentValues.TAG, "문서를 가져올수없습니다: ", exception)
            }
    }




}