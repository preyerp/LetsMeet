package com.where.letsmeet


import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBindings
import com.bumptech.glide.Glide

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.where.letsmeet.databinding.ActivityMapsBinding
import com.where.letsmeet.databinding.BottomSheetLayoutBinding
import com.where.letsmeet.databinding.MenuRecyclerItemBinding
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.where.letsmeet.databinding.BottomSheetRadioBinding
import com.where.letsmeet.databinding.CustomDialogHelpBinding
import kotlinx.android.synthetic.main.bottom_sheet_radio.confirmButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    data class Restaurant(
        val name: String,
        val address: String,
        val location: GeoPoint,
        val coupon: String,
        val menus: List<Menu>
    )

    data class Menu(
        val menuName: String,
        val menuPrice: String,
        val menuImage: String
    )

    data class MarkerData(
        val title: String,
        val snippet: String,
        val coupon: String,
        val menuList: List<Menu>
        // Add any other relevant data
    )

    private val restaurants = mutableMapOf<String, Restaurant>()

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private var blueTitle = ""

    private var finalLat = 0.0
    private var finalLon = 0.0
    private var finalName = "최종역"

    private var purpose = ""

    var tempLat = 37.476538
    var tempLon = 126.981544

    private val markerList = mutableListOf<MarkerData>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)


        finalLat = intent.getDoubleExtra("finalStationLat",0.0)
        finalLon = intent.getDoubleExtra("finalStationLon",0.0)
        finalName = intent.getStringExtra("finalStationName").toString()
        purpose = intent.getStringExtra("목적").toString()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)


        binding.searchButton.setOnClickListener {
            // 주변 식당 정보 가져오기, 마커표시
            GlobalScope.launch(Dispatchers.Main) {
                mMap.clear()
                val finalMeet = LatLng(finalLat, finalLon)
                mMap.addMarker(MarkerOptions().position(finalMeet).title("${finalName}").snippet("최종 목적지").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(finalMeet, 15f))
                mMap.setInfoWindowAdapter(null)

                fetchAndDisplayRestaurants()
                // val restaurantData = fetchRestaurantData()
                // System.out.println("restaurantData2 : $restaurantData")
                // displayWebRestaurants(restaurantData)
//                val radius = 1000.0
//                var target = findNearbyRestaurant(finalLat, finalLon, restaurantData, radius)
//                System.out.println("범위 내 식당 : $target")
//                if (target.isNotEmpty()) {
//                    displayWebRestaurants(target, restaurantData)
//                }
            }
        }
        binding.helpButton.setOnClickListener {
            showCustomDialog()
        }

        binding.purposeButton.setOnClickListener {
            showBottomSheetDialogWithRadioButtons()
        }

        binding.backButton.setOnClickListener {
            finish()
        }

    }
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // 목적지 마커표시, 화면이동
        val finalMeet = LatLng(finalLat, finalLon)
        val testCoord = LatLng(37.534854, 126.972707)
        mMap.addMarker(MarkerOptions().position(finalMeet).title("${finalName}").snippet("최종 목적지").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)))
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(finalMeet, 15f))
        mMap.setInfoWindowAdapter(null)

        mMap.setOnInfoWindowClickListener { clickedMarker ->
            val markerData = markerList.find { it.title == clickedMarker.title && it.snippet == clickedMarker.snippet }
            System.out.println("markerData : ${markerData}")
            markerData?.let { data ->
                showBottomSheetDialog(data)
            }
        }

    }
    //  ===============================================================등록 업체 정보 불러오기==================================================

    suspend fun fetchRestaurantData(): Map<String, Restaurant> = withContext(Dispatchers.IO) {
        val db = FirebaseFirestore.getInstance()
        val restaurantData = mutableMapOf<String, Restaurant>()
        val documents = db.collection("stores").get().await()
        for (document in documents) {
            val storeId = document.id
            val name = document.getString("매장이름") ?: ""
            val address = document.getString("매장위치") ?: ""
            val latitude = document.getDouble("매장좌표X") ?: 0.0
            val longitude = document.getDouble("매장좌표Y") ?: 0.0
            val coupon = document.getString("매장쿠폰") ?: ""
            val location = GeoPoint(latitude, longitude)
            val menuDocuments = db.collection("stores").document(storeId).collection("menu").get().await()
            val menus = menuDocuments.mapNotNull { menuDocument ->
                val menuName = menuDocument.getString("menuName") ?: "메뉴 없음"
                val menuPrice = menuDocument.getString("menuPrice") ?: "메뉴 없음"
                val menuImage = menuDocument.getString("menuImage") ?: "메뉴 없음"
                Menu(menuName, menuPrice, menuImage)
            }
            restaurantData[storeId] = Restaurant(name, address, location, coupon, menus)
            System.out.println("restaurantData1 : ${restaurantData[storeId]}")
        }
        restaurantData

    }


//    fun getRestaurantInfo(restaurantsName: String, restaurantsAddress: String, restaurants: Map<String, Restaurant>): GeoPoint? {  // 식당 이름으로 식당 위치좌표 반환
//        for ((_, restaurant) in restaurants) {
//            val name = restaurant.name
//            val address = restaurant.address
//            val location = restaurant.location
//            if (name == restaurantsName && address == restaurantsAddress ){
//                return location
//            }
//        }
//        return GeoPoint(0.0, 0.0)
//    } 식당이름으로 식당 위치좌표 불러오는 함수였는데 현재 쓸모없음

//    fun findNearbyRestaurant(     // 후보지 내 식당들 구하는 함수였지만 쓸모없어짐
//        lat: Double,
//        lon: Double,
//        restaurants: Map<String, Restaurant>,
//        radius: Double = 1000.0
//    ): List<String> {
//        val nearbyRestaurant = mutableListOf<String>()
//
//        for ((_, restaurant) in restaurants) {
//            val location = restaurant.location
//            if (location != null) {
//                val distance = getDistance(lat, lon, location.latitude, location.longitude)
//                if (distance <= radius) {
//                    nearbyRestaurant.add(restaurant.name)
//                }
//            }
//        }
//        if(nearbyRestaurant.isEmpty()){
//            System.out.println("범위 내 식당 없음!!!")
//
//        }
//
//        return nearbyRestaurant
//    }

    //  ===============================================================등록 업체 정보 불러오기==================================================



    private suspend fun fetchAndDisplayRestaurants() {
        // Create the service instance
        val placesService = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/maps/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlacesService::class.java)
        // Fetch nearby restaurants
        val response = withContext(Dispatchers.IO) {
            placesService.getNearbyRestaurants("$finalLat, $finalLon", 1000, purpose, "YOUR_API_Key")
        }
        System.out.println("purpose : ${purpose}")
        if (response.results.isNotEmpty()) {
            // Process and display the restaurant data, e.g., adding markers
            // Filter and add markers
            response.results.filter { it.rating >= 2.5 }.forEach { result ->
                val location = LatLng(result.geometry.location.lat, result.geometry.location.lng)
                if (getDistance(finalLat, finalLon, location.latitude, location.longitude) <= 1000) {
                    runOnUiThread {
                        try {
                            mMap.addMarker(MarkerOptions().position(location).title(result.name).snippet("별점: ${result.rating}, 리뷰 수: ${result.user_ratings_total}"))
                        } catch (e: Exception) {
                            Log.e("Marker", "Error adding marker: ${e.localizedMessage}")
                        }
                    }
                }
            }
        } else {
            // Show a toast message when there are no nearby restaurants
            Toast.makeText(this, "선택하신 유형의 구글 place 기반 장소가 주변에 없습니다.", Toast.LENGTH_SHORT).show()
        }



    }

    private suspend fun displayWebRestaurants(restaurants: Map<String, Restaurant>) {
        for ((_, restaurant) in restaurants) {
            if (getDistance(finalLat, finalLon, restaurant.location.latitude, restaurant.location.longitude) <= 1000.0 ){
                val restaurantName = restaurant.name
                val restaurantLocation = LatLng(restaurant.location.latitude, restaurant.location.longitude)
                val restaurantAddress = restaurant.address
                val restaurantCoupon = restaurant.coupon
                val menus = restaurant.menus
                System.out.println("menus : $menus")
                mMap.addMarker(MarkerOptions().position(restaurantLocation).title(restaurantName).snippet(restaurantAddress).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))
                markerList.add(MarkerData(restaurantName, restaurantAddress, restaurantCoupon, menus))
            }
        }

    }

    private fun showBottomSheetDialog(data: MarkerData) {
        val bottomSheetDialog = BottomSheetDialog(this, R.style.CustomBottomSheetDialogTheme)
        val binding = BottomSheetLayoutBinding.inflate(layoutInflater)
        binding.root.background = ContextCompat.getDrawable(this, R.drawable.rounded_dialog)


        binding.restaName.text = data.title
        binding.restaAddress.text = data.snippet
        binding.restaCoupon.text = data.coupon

        binding.menuRecyclerView.adapter = MyAdapter(this, data.menuList)
        binding.menuRecyclerView.layoutManager = LinearLayoutManager(this)


        bottomSheetDialog.setContentView(binding.root)
        val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.background = null

        bottomSheetDialog.show()

    }


    private fun showBottomSheetDialogWithRadioButtons() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val binding = BottomSheetRadioBinding.inflate(layoutInflater)

        binding.root.background = ContextCompat.getDrawable(this, R.drawable.rounded_dialog)




        binding.choicePurpose1.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButRestaurant -> purpose = "restaurant"
                R.id.radButCafe -> purpose = "cafe"
            }
        }
        binding.choicePurpose2.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButBakery -> purpose = "bakery"
                R.id.radButBar -> purpose = "bar"
            }
        }
        binding.choicePurpose3.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButMovie -> purpose = "movie_theater"
                R.id.radButBook -> purpose = "library"
            }
        }
        binding.choicePurpose4.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButGallery -> purpose = "art_gallery"
                R.id.radButMuseum -> purpose = "museum"
            }
        }
        binding.choicePurpose5.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButSpa -> purpose = "spa"
                R.id.radButBowling -> purpose = "bowling_alley"
            }
        }
        binding.choicePurpose6.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButTour -> purpose = "tourist_attraction"
            }
        }
        binding.choicePurpose7.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButCloth -> purpose = "clothing_store"
                R.id.radButDepart -> purpose = "department_store"
            }
        }
        binding.choicePurpose8.setOnCheckedChangeListener { group, checkedId ->
            when(checkedId){
                R.id.radButMall -> purpose = "shopping_mall"
            }
        }
        binding.confirmButton.setOnClickListener {
            bottomSheetDialog.dismiss()
        }


        bottomSheetDialog.setContentView(binding.root)
        bottomSheetDialog.show()
    }

    private fun showCustomDialog() {
        val dialog = Dialog(this)
        val binding = CustomDialogHelpBinding.inflate(layoutInflater)
        dialog.setContentView(binding.root)


        binding.closeButton.setOnClickListener {
            dialog.dismiss() // Close the dialog
        }

        dialog.show()
    }



    class MyAdapter(private val context: Context, private val menuData: List<Menu>) : RecyclerView.Adapter<MyAdapter.ViewHolder>() {

        inner class ViewHolder(private val binding: MenuRecyclerItemBinding) : RecyclerView.ViewHolder(binding.root){
            fun bind(menuPart: Menu) {
                val defaultImage = R.drawable.nopic
                val loadingImage = R.drawable.loadingimg
                Glide.with(context).load(menuPart.menuImage).placeholder(loadingImage).error(defaultImage).fallback(defaultImage).into(binding.menuImg)


//                Glide.with(this)
//                    .load(url) // 불러올 이미지 url
//                    .placeholder(defaultImage) // 이미지 로딩 시작하기 전 표시할 이미지
//                    .error(defaultImage) // 로딩 에러 발생 시 표시할 이미지
//                    .fallback(defaultImage) // 로드할 url 이 비어있을(null 등) 경우 표시할 이미지
//                    .into(binding.menuImg) // 이미지를 넣을 뷰

                binding.menuNameText.text = menuPart.menuName
                binding.menuPriceText.text = menuPart.menuPrice
                System.out.println("menuPart.menuName : ${menuPart.menuName}")
                System.out.println("menuPart.menuPrice : ${menuPart.menuPrice}")
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = MenuRecyclerItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun getItemCount() = menuData.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(menuData[position])
            System.out.println("position : $position")
            System.out.println("menuData.size : ${menuData.size}")

        }


    }




    fun getDistance(    // 거리구하는 함수
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val earthRadius = 6371000.0

        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }


}

// Response data class
data class PlacesResponse(
    val results: List<Place>
)

// Place data class
data class Place(
    val geometry: Geometry,
    val name: String,
    val rating: Float,
    val user_ratings_total: Int // number of user ratings
)

// Geometry data class
data class Geometry(
    val location: Location
)

// Location data class
data class Location(
    val lat: Double,
    val lng: Double
)

// Define the Places API service
interface PlacesService {
    @GET("place/nearbysearch/json")
    suspend fun getNearbyRestaurants(
        @Query("location") location: String,
        @Query("radius") radius: Int,
        @Query("type") type: String,
        @Query("key") apiKey: String
    ): PlacesResponse
}