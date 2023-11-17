package com.where.letsmeet

import android.content.ContentValues
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.where.letsmeet.databinding.ActivitySubwayBinding
import com.where.letsmeet.databinding.RecyclerviewItemBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.util.ArrayList
import kotlin.math.PI
import kotlin.math.cos
import java.util.PriorityQueue

class SubwayActivity : AppCompatActivity() {
    data class Station(
        val name: String,
        val location: GeoPoint,
        val lines: List<Line>
    )
    data class Line(
        val name: String,
        val beforeStation: String?,
        val beforeStationTime: Long?,
        val nextStation: String?,
        val nextStationTime: Long?,
        val transfers: List<Transfer>?
    )
    data class Transfer(
        val lineFrom: String?,
        val transferTime: Long?
    )
    data class RouteSegment(
        val line: String,
        val station: String,
        val travelTime: Long? = null,
        val transferTime: Long? = null
    )

    private val stations = mutableMapOf<String, Station>()


    private var list = listOf<String>()


    private lateinit var binding: ActivitySubwayBinding

    private var finalStationLat = 0.0
    private var finalStationLon = 0.0
    private var finalStationName = "최종역"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubwayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lists = intent.getStringArrayListExtra("출발지 리스트")!!
        System.out.println("list파라미터 : $lists")
        var userStation = intent.getStringExtra("출발지")

        fetchSubwayData(lists, userStation!!)

        var meetPurpose = intent.getStringExtra("목적")

        binding.showButton.setOnClickListener {
            val intent = Intent(this,MapsActivity::class.java)
            intent.putExtra("finalStationLat", finalStationLat)
            intent.putExtra("finalStationLon", finalStationLon)
            intent.putExtra("finalStationName", finalStationName)
            intent.putExtra("목적", meetPurpose)
            startActivity(intent)
        }
        binding.backButton.setOnClickListener {
            finish()
        }


    }




    private fun fetchSubwayData(lists: ArrayList<String>, userStart: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("finalSubway")    // 컬렉션 이름 변경
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val name = document.getString("name") ?: continue
                    val location = document.getGeoPoint("location") ?: continue
                    val linesData = document.get("lines") as? List<Map<String, Any>> ?: continue
                    val lines = linesData.mapNotNull { lineData ->
                        val lineName = lineData["name"] as? String ?: return@mapNotNull null
                        val beforeStation = lineData["beforeStation"] as? String
                        val beforeStationTime = (lineData["beforeStationTime"] as? Number)?.toLong()
                        val nextStation = lineData["nextStation"] as? String
                        val nextStationTime = (lineData["nextStationTime"] as? Number)?.toLong()
                        val transfersData = lineData["transfer"] as? List<Map<String, Any>> ?: emptyList()
                        val transfers = transfersData.mapNotNull { transferData ->
                            val lineFrom = transferData["lineFrom"] as? String ?: return@mapNotNull null
                            val transferTime = (transferData["transferTime"] as? Number)?.toLong() ?: return@mapNotNull null
                            Transfer(lineFrom, transferTime)
                        }
                        Line(lineName, beforeStation, beforeStationTime, nextStation, nextStationTime, transfers)
                    }
                    stations[name] = Station(name, location, lines)
                }


                val points = mutableListOf<Pair<Double, Double>>()
                for (locat in lists){
                    val locationCoord = getStationLocation(locat ,stations)
                    if (locationCoord != null) {
                        points.add(Pair(locationCoord.latitude, locationCoord.longitude))
                    }
                }
                var finalPoint: Pair<Double, Double>? = null
                finalPoint = findFinalPoint(points)    // 후보지 위치 구하기
                System.out.println("직선거리기준 중점 : $finalPoint")
                var pointLat = finalPoint.first
                var pointLon = finalPoint.second

                var radius = 3000.0 // 후보지 반경

                var targetStations = findNearbyStations(pointLat, pointLon, stations, radius)     // 중간지점 후보지 내 지하철역들
                while (targetStations.isEmpty()){
                    radius += 1000.0
                    targetStations = findNearbyStations(pointLat, pointLon, stations, radius)
                    System.out.println("radius : $radius")
                }
                System.out.println("======================================================")
                System.out.println("중점에서 반경 3km(후보지) 내 지하철역 목록 : $targetStations")
                val bestStation = findStationWithSmallestStdDev(lists, targetStations)     // 최종 목적지
                finalStationName = bestStation
                System.out.println("======================================================")
                System.out.println("가장 적합한(표준편차가 가장 작은) 지하철역 : $bestStation")
                val tempFinal = getStationLocation(bestStation ,stations)
                if (tempFinal != null){
                    finalStationLat = tempFinal.latitude
                    finalStationLon = tempFinal.longitude
                }
                binding.finalStationText.text = "최종 목적지: $bestStation 역"

                // 최종 목적지까지의 경로 및 시간
                val finalPath = dijkstraOptimized(userStart, bestStation, stations)
                val totalTime = calculateTotalTime(finalPath)

                binding.totalTimeTextView.text = "총 소요 시간: $totalTime 분"
                binding.routeRecyclerView.adapter = RouteAdapter(finalPath)
                binding.routeRecyclerView.layoutManager = LinearLayoutManager(this)

            }
            .addOnFailureListener { exception ->
                System.out.println("에러")
                Log.w(ContentValues.TAG, "문서를 가져올수없습니다: ", exception)
            }
    }

    fun getStationLocation(stationName: String, stations: Map<String, Station>): GeoPoint? {
        return stations[stationName]?.location
    }


    fun dijkstraOptimized(
        startingStation: String,
        targetStation: String,
        stations: Map<String, Station>
    ): List<RouteSegment> {
        val tentativeTimes = mutableMapOf<String, Pair<Long, RouteSegment>>()
        val queue = PriorityQueue<Pair<String, Long>>(compareBy { it.second })
        val path = mutableListOf<RouteSegment>()
        stations.forEach { (name, _) ->
            tentativeTimes[name] = Pair(Long.MAX_VALUE, RouteSegment("", "", null))
        }
        tentativeTimes[startingStation] = Pair(0, RouteSegment("", startingStation, null))

        queue.add(Pair(startingStation, 0))

        while (queue.isNotEmpty()) {
            val (currentStation, currentTime) = queue.poll()

            if (currentStation == targetStation) {
                path.add(RouteSegment("도착", targetStation, null, null))
                break
            }

            for (line in stations[currentStation]?.lines.orEmpty()) {
                val neighbors = listOfNotNull(line.beforeStation, line.nextStation)

                for (neighbor in neighbors) {
                    val neighborTime = if (neighbor == line.beforeStation) line.beforeStationTime else line.nextStationTime
                    val transferTime = line.transfers?.firstOrNull { it.lineFrom == tentativeTimes[currentStation]?.second?.line }?.transferTime ?: 0
                    val newTime = currentTime + (neighborTime ?: 0) + transferTime

                    if (newTime < tentativeTimes[neighbor]?.first ?: Long.MAX_VALUE) {
                        tentativeTimes[neighbor] = Pair(newTime, RouteSegment(line.name, currentStation, neighborTime, transferTime))
                        queue.add(Pair(neighbor, newTime))
                    }
                }
            }
        }


        var current = targetStation

        while (current != startingStation) {
            val routeSegment = tentativeTimes[current]?.second ?: break
            path.add(0, routeSegment)
            current = routeSegment.station
        }

        return path
    }


    private fun calculateTotalTime(path: List<RouteSegment>): Long {  // 소요시간 계산하는 함수
        return path.sumOf { (it.travelTime ?: 0) + (it.transferTime ?: 0) }
    }

    class RouteAdapter(private val route: List<RouteSegment>) : RecyclerView.Adapter<RouteAdapter.ViewHolder>() {
        private var start = "시작"
        private var temp = "시작"
        inner class ViewHolder(private val binding: RecyclerviewItemBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(routeSegment: RouteSegment, arriveLine: String) {
                if (start == "시작") {
                    binding.transferStationLayout.visibility = View.VISIBLE
                    binding.normalStationLayout.visibility = View.GONE
                    binding.transLineNameTextView.text = arriveLine
                    binding.transferStationNameTextView.text = routeSegment.station
                    start = "출발함"
                }
                else if (routeSegment.line == "도착") {
                    binding.beforeTrans.visibility = View.GONE
                    binding.transferTimeLayout.visibility = View.GONE
                    binding.transferStationLayout.visibility = View.GONE
                    binding.normalStationLayout.visibility = View.GONE
                    binding.endStationLayout.visibility = View.VISIBLE

                    binding.endLineTextView.text = arriveLine
                    binding.endStationTextView.text = routeSegment.station
                }
                else if (routeSegment.transferTime != 0L && routeSegment.transferTime != null){
                    binding.beforeTrans.visibility = View.VISIBLE
                    binding.transferTimeLayout.visibility = View.VISIBLE
                    binding.transferStationLayout.visibility = View.VISIBLE
                    binding.normalStationLayout.visibility = View.GONE

                    binding.beforeLineNameTextView.text = arriveLine
                    binding.beforeStationNameTextView.text = routeSegment.station
                    binding.transferTextView.text = "환승"
                    binding.transferTimeTextView.text = "${routeSegment.transferTime} 분"
                    binding.transLineNameTextView.text = routeSegment.line
                    binding.transferStationNameTextView.text = routeSegment.station
                }
                else{
                    binding.beforeTrans.visibility = View.GONE
                    binding.transferTimeLayout.visibility = View.GONE
                    binding.transferStationLayout.visibility = View.GONE

                    binding.lineNameTextView.text = routeSegment.line
                    binding.stationNameTextView.text = routeSegment.station
                }

            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = RecyclerviewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun getItemCount(): Int = route.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (start == "시작") {
                temp = route[position].line
                holder.bind(route[position], temp)
            }
            else if (route[position].transferTime != null && route[position].transferTime != 0L) {
                holder.bind(route[position],temp)
            }
            else{
                if (route[position].line != "도착"){
                    temp = route[position].line
//                    System.out.println("temp : $temp")
                }
                holder.bind(route[position],temp)
//                System.out.println("route[position] : ${route[position]}")
            }

        }
    }



    fun findFinalPoint(coordinates: List<Pair<Double, Double>>): Pair<Double, Double> { // 후보지 위치 구하는 함수
        if (coordinates.size == 2) {
            return Pair((coordinates[0].first + coordinates[1].first) / 2, (coordinates[0].second + coordinates[1].second) / 2)
        }

        var maxDistance = Double.MIN_VALUE
        var furthestPair: Pair<Pair<Double, Double>, Pair<Double, Double>>? = null

        for (i in coordinates.indices) {
            for (j in i + 1 until coordinates.size) {
                val distance = getDistance(coordinates[i].first, coordinates[i].second, coordinates[j].first, coordinates[j].second)
                if (distance > maxDistance) {
                    maxDistance = distance
                    furthestPair = Pair(coordinates[i], coordinates[j])

                }
            }
        }
        System.out.println("서로 가장 멀리 떨어진 두 좌표 : $furthestPair")
        val circleRadius = maxDistance / 2
        val circleCenter = Pair((furthestPair!!.first.first + furthestPair.second.first) / 2, (furthestPair.first.second + furthestPair.second.second) / 2)
        var circleOut: Pair<Double, Double>? = null
        var maxDistanceFromCenter = Double.MIN_VALUE

        for (coordinate in coordinates) {
            if (coordinate == furthestPair.first || coordinate == furthestPair.second) continue
            val distanceFromCenter = getDistance(coordinate.first, coordinate.second, circleCenter.first, circleCenter.second)
            if (distanceFromCenter > circleRadius) {
                if (distanceFromCenter > maxDistanceFromCenter) {
                    maxDistanceFromCenter = distanceFromCenter
                    circleOut = coordinate
                }
            }
        }

        return if (circleOut == null) {
            circleCenter
        } else {
            val p1 = furthestPair.first
            val p2 = furthestPair.second
            val p3 = circleOut

            val circumcenter = findCircumcenter(p1, p2, p3)

            return circumcenter
        }
    }
    fun findCircumcenter(p1: Pair<Double, Double>, p2: Pair<Double, Double>, p3: Pair<Double, Double>): Pair<Double, Double> {  // 세점을 모두 포함하는 원의 중심좌표 구하는 함수
        fun toCartesian(p: Pair<Double, Double>, ref: Pair<Double, Double>): Pair<Double, Double> {
            val R = 6371000.0 // 대략적인 지구 반지름 미터 단위
            val x = (p.second - ref.second) * cos((p.first + ref.first) / 2 * PI / 180) * R
            val y = (p.first - ref.first) * R
            return Pair(x, y)
        }

        fun toGeographic(coord: Pair<Double, Double>, ref: Pair<Double, Double>): Pair<Double, Double> {
            val R = 6371000.0 // 대략적인 지구 반지름 미터 단위
            val latitude = coord.second / R + ref.first
            val longitude = coord.first / (R * cos((latitude + ref.first) / 2 * PI / 180)) + ref.second
            return Pair(latitude, longitude)
        }

        fun findCircumcenterCartesian(p1: Pair<Double, Double>, p2: Pair<Double, Double>, p3: Pair<Double, Double>): Pair<Double, Double> {
            val D = 2 * (p1.first * (p2.second - p3.second) + p2.first * (p3.second - p1.second) + p3.first * (p1.second - p2.second))

            val Ux = ((p1.first * p1.first + p1.second * p1.second) * (p2.second - p3.second) + (p2.first * p2.first + p2.second * p2.second) * (p3.second - p1.second) + (p3.first * p3.first + p3.second * p3.second) * (p1.second - p2.second)) / D
            val Uy = ((p1.first * p1.first + p1.second * p1.second) * (p3.first - p2.first) + (p2.first * p2.first + p2.second * p2.second) * (p1.first - p3.first) + (p3.first * p3.first + p3.second * p3.second) * (p2.first - p1.first)) / D

            return Pair(Ux, Uy)
        }

        val refPoint = p1
        val c1 = toCartesian(p1, refPoint)
        val c2 = toCartesian(p2, refPoint)
        val c3 = toCartesian(p3, refPoint)

        val circumcenterCartesian = findCircumcenterCartesian(c1, c2, c3)
        return toGeographic(circumcenterCartesian, refPoint)
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


    fun findNearbyStations(     // 후보지 내 지하철역들 구하는 함수
        lat: Double,
        lon: Double,
        stations: Map<String, Station>,
        radius: Double = 3000.0
    ): List<String> {
        val nearbyStations = mutableListOf<String>()

        for ((_, station) in stations) {
            val location = station.location
            if (location != null) {
                val distance = getDistance(lat, lon, location.latitude, location.longitude)
                if (distance <= radius) {
                    nearbyStations.add(station.name)
                }
            }
        }
        if(nearbyStations.isEmpty()){
            System.out.println("범위 내 지하철역없음!!!")

        }

        return nearbyStations
    }

    fun findStationWithSmallestStdDev(      // 소요시간 표준편차가 가장 작은 지하철역 구하는 함수
        startingStations: List<String>,
        targetStations: List<String>
    ): String {
        var smallestStdDev = Double.MAX_VALUE
        var smallestSumTimes = Int.MAX_VALUE
        var bestStation = "null"

        for (target in targetStations) {
            val times = mutableListOf<Long>()
            var timeSum = 0
            for (start in startingStations) {
                val path = dijkstraOptimized(start, target, stations)
                val totalTime = calculateTotalTime(path)
                times.add(totalTime)
                timeSum += totalTime.toInt()
            }
            System.out.println("-----------------------------------")
            System.out.println("targetStation : $target")
            System.out.println("target역 까지의 시간 : $times")
            System.out.println("시간 합산 : $timeSum")
            val stdDev = calculateStandardDeviation(times)
            System.out.println("표준편차 : $stdDev")
            if (stdDev < smallestStdDev && timeSum < smallestSumTimes) {
                smallestStdDev = stdDev
                smallestSumTimes = timeSum
                bestStation = target
            }
        }

        return bestStation
    }

    fun calculateStandardDeviation(times: List<Long>): Double {     //표준편차 계산하는 함수
        if (times.isEmpty()) return Double.MAX_VALUE

        val mean = times.average()
        val variance = times.map { (it - mean) * (it - mean) }.average()
        return Math.sqrt(variance)
    }



}