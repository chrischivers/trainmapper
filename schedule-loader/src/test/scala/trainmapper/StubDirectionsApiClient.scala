package trainmapper
import cats.effect.IO
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import org.http4s.{HttpService, StaticFile}
import org.http4s.dsl.io.NotFound
import io.circe.parser._
import org.http4s.circe._
import org.http4s.dsl.io._

object StubDirectionsApiClient extends StrictLogging {

  def apply(stubResponse: Json = stubJsonResponse) = HttpService[IO] {
    case request =>
      logger.info(s"Getting stub directions for request $request")
      Ok(stubResponse)
  }

  val stubJsonResponse: Json =
    parse(
      """
      |
      |{
      |	"geocoded_waypoints": [{
      |			"geocoder_status": "OK",
      |			"place_id": "ChIJWfEiTXUCdkgRWK1oIILmrOo",
      |			"types": [
      |				"street_address"
      |			]
      |		},
      |		{
      |			"geocoder_status": "OK",
      |			"place_id": "ChIJTUVgrump2EcRZksVqHlGkck",
      |			"types": [
      |				"street_address"
      |			]
      |		}
      |	],
      |	"routes": [{
      |		"bounds": {
      |			"northeast": {
      |				"lat": 51.4527957,
      |				"lng": 0.0140367
      |			},
      |			"southwest": {
      |				"lat": 51.4496714,
      |				"lng": -0.0013456
      |			}
      |		},
      |		"copyrights": "Map data ©2018 Google",
      |		"legs": [{
      |			"arrival_time": {
      |				"text": "10:09",
      |				"time_zone": "Europe/London",
      |				"value": 1532941792
      |			},
      |			"departure_time": {
      |				"text": "10:03",
      |				"time_zone": "Europe/London",
      |				"value": 1532941426
      |			},
      |			"distance": {
      |				"text": "1.3 km",
      |				"value": 1301
      |			},
      |			"duration": {
      |				"text": "6 mins",
      |				"value": 366
      |			},
      |			"end_address": "2 Glenmere Row, Lee, London SE12 8RH, UK",
      |			"end_location": {
      |				"lat": 51.4497531,
      |				"lng": 0.0135072
      |			},
      |			"start_address": "1 Fernbrook Rd, London SE13 5NF, UK",
      |			"start_location": {
      |				"lat": 51.4520221,
      |				"lng": -0.0009226999999999999
      |			},
      |			"steps": [{
      |					"distance": {
      |						"text": "0.2 km",
      |						"value": 241
      |					},
      |					"duration": {
      |						"text": "3 mins",
      |						"value": 194
      |					},
      |					"end_location": {
      |						"lat": 51.45176559999999,
      |						"lng": 0.0001943
      |					},
      |					"html_instructions": "Walk to Hither Green Station",
      |					"polyline": {
      |						"points": "cf`yHvDg@|@GLCDE@I@M?IAKE_@s@CGCEKSNUFK\\WJGBC@CDIBGBBvB}B"
      |					},
      |					"start_location": {
      |						"lat": 51.4520221,
      |						"lng": -0.0009226999999999999
      |					},
      |					"steps": [{
      |							"distance": {
      |								"text": "0.1 km",
      |								"value": 106
      |							},
      |							"duration": {
      |								"text": "1 min",
      |								"value": 71
      |							},
      |							"end_location": {
      |								"lat": 51.4527957,
      |								"lng": -0.0008849
      |							},
      |							"html_instructions": "Walk <b>north-west</b> towards <b>Fernbrook Rd</b>",
      |							"polyline": {
      |								"points": "cf`yHvDg@|@GLCDE@I@M?IAKE_@s@CGCEKS"
      |							},
      |							"start_location": {
      |								"lat": 51.4520221,
      |								"lng": -0.0009226999999999999
      |							},
      |							"travel_mode": "WALKING"
      |						},
      |						{
      |							"distance": {
      |								"text": "55 m",
      |								"value": 55
      |							},
      |							"duration": {
      |								"text": "1 min",
      |								"value": 42
      |							},
      |							"end_location": {
      |								"lat": 51.4523923,
      |								"lng": -0.00042
      |							},
      |							"html_instructions": "Turn <b>right</b> onto <b>Fernbrook Rd</b>",
      |							"maneuver": "turn-right",
      |							"polyline": {
      |								"points": "_k`yHnDNUFK\\WJGBC@CDIBG"
      |							},
      |							"start_location": {
      |								"lat": 51.4527957,
      |								"lng": -0.0008849
      |							},
      |							"travel_mode": "WALKING"
      |						},
      |						{
      |							"distance": {
      |								"text": "80 m",
      |								"value": 80
      |							},
      |							"duration": {
      |								"text": "1 min",
      |								"value": 81
      |							},
      |							"end_location": {
      |								"lat": 51.45176559999999,
      |								"lng": 0.0001943
      |							},
      |							"html_instructions": "Take entrance <span class=\"location\">Hither Green Rail Station</span>",
      |							"polyline": {
      |								"points": "ih`yHvAvB}B"
      |							},
      |							"start_location": {
      |								"lat": 51.452374,
      |								"lng": -0.0004419999999999999
      |							},
      |							"travel_mode": "WALKING"
      |						}
      |					],
      |					"travel_mode": "WALKING"
      |				},
      |				{
      |					"distance": {
      |						"text": "1.0 km",
      |						"value": 1006
      |					},
      |					"duration": {
      |						"text": "2 mins",
      |						"value": 120
      |					},
      |					"end_location": {
      |						"lat": 51.4496714,
      |						"lng": 0.0140172
      |					},
      |					"html_instructions": "Train towards Dartford",
      |					"polyline": {
      |						"points": "qd`yHe@HFPq@t@kDn@qDd@aEZaEPqDPaDB[Bm@J_CL{BDi@LoCDaAHcBTgEN_DXuFF}@@_@B_@HaBNmCLB"
      |					},
      |					"start_location": {
      |						"lat": 51.45176559999999,
      |						"lng": 0.0001943
      |					},
      |					"transit_details": {
      |						"arrival_stop": {
      |							"location": {
      |								"lat": 51.4496714,
      |								"lng": 0.0140172
      |							},
      |							"name": "Lee"
      |						},
      |						"arrival_time": {
      |							"text": "10:09",
      |							"time_zone": "Europe/London",
      |							"value": 1532941740
      |						},
      |						"departure_stop": {
      |							"location": {
      |								"lat": 51.45176559999999,
      |								"lng": 0.0001943
      |							},
      |							"name": "Hither Green Station"
      |						},
      |						"departure_time": {
      |							"text": "10:07",
      |							"time_zone": "Europe/London",
      |							"value": 1532941620
      |						},
      |						"headsign": "Dartford",
      |						"line": {
      |							"agencies": [{
      |								"name": "southeastern",
      |								"phone": "011 44 345 322 7021",
      |								"url": "https://www.southeasternrailway.co.uk/"
      |							}],
      |							"color": "#1b1650",
      |							"name": "Dartford - London Charing Cross",
      |							"short_name": "southeastern",
      |							"text_color": "#ffffff",
      |							"vehicle": {
      |								"icon": "//maps.gstatic.com/mapfiles/transit/iw2/6/rail2.png",
      |								"local_icon": "//maps.gstatic.com/mapfiles/transit/iw2/6/uk-rail.png",
      |								"name": "Train",
      |								"type": "HEAVY_RAIL"
      |							}
      |						},
      |						"num_stops": 1
      |					},
      |					"travel_mode": "TRANSIT"
      |				},
      |				{
      |					"distance": {
      |						"text": "54 m",
      |						"value": 54
      |					},
      |					"duration": {
      |						"text": "1 min",
      |						"value": 52
      |					},
      |					"end_location": {
      |						"lat": 51.4497531,
      |						"lng": 0.0135072
      |					},
      |					"html_instructions": "Walk to 2 Glenmere Row, Lee, London SE12 8RH, UK",
      |					"polyline": {
      |						"points": "mw_yHsvAI`CE["
      |					},
      |					"start_location": {
      |						"lat": 51.4496714,
      |						"lng": 0.0140172
      |					},
      |					"steps": [{
      |							"distance": {
      |								"text": "44 m",
      |								"value": 44
      |							},
      |							"duration": {
      |								"text": "1 min",
      |								"value": 45
      |							},
      |							"end_location": {
      |								"lat": 51.449718,
      |								"lng": 0.013373
      |							},
      |							"html_instructions": "Take exit <span class=\"location\">Lee Rail Station</span>",
      |							"polyline": {
      |								"points": "mw_yHsvAI`C"
      |							},
      |							"start_location": {
      |								"lat": 51.4496714,
      |								"lng": 0.0140172
      |							},
      |							"travel_mode": "WALKING"
      |						},
      |						{
      |							"distance": {
      |								"text": "10 m",
      |								"value": 10
      |							},
      |							"duration": {
      |								"text": "1 min",
      |								"value": 7
      |							},
      |							"end_location": {
      |								"lat": 51.4497531,
      |								"lng": 0.0135072
      |							},
      |							"polyline": {
      |								"points": "ww_yHqrAE["
      |							},
      |							"start_location": {
      |								"lat": 51.449718,
      |								"lng": 0.013373
      |							},
      |							"travel_mode": "WALKING"
      |						}
      |					],
      |					"travel_mode": "WALKING"
      |				}
      |			],
      |			"traffic_speed_entry": [],
      |			"via_waypoint": []
      |		}],
      |		"overview_polyline": {
      |			"points": "cf`yHvDo@jAIFW@UGs@uAVa@h@_@NYBBvB}BHFPq@t@kDn@qDd@aEZaEb@sIFiAl@uLt@mOf@sJXoFLBI`CE["
      |		},
      |		"summary": "",
      |		"warnings": [
      |			"Walking directions are in beta.    Use caution – This route may be missing sidewalks or pedestrian paths."
      |		],
      |		"waypoint_order": []
      |	}],
      |	"status": "OK"
      |}
      |
      |
    """.stripMargin).right.get
}
