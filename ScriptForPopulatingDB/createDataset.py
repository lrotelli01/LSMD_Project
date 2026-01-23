import csv
import json
import random
from datetime import timedelta, datetime
from faker import Faker
import requests
import gzip
import io
import os

# Initialize Faker
fake = Faker()
Faker.seed(42)

# Create output folder
os.makedirs("output_entities", exist_ok=True)

# Helper functions

def generate_customer(available_property_ids=[]):
    first_name = fake.first_name()
    last_name = fake.last_name()
    
    # Select random favored properties
    favored_ids = []
    if available_property_ids:
        k = random.randint(0, min(3, len(available_property_ids)))
        favored_ids = random.sample(available_property_ids, k)

    return {
        "id": fake.uuid4(),
        "username": fake.user_name(),
        "email": fake.email(),
        "password": fake.password(),
        "name": first_name,
        "surname": last_name,
        "birthdate": fake.date_of_birth(minimum_age=18, maximum_age=90).isoformat(),
        "phoneNumber": fake.phone_number(),
        "paymentMethod": {
            "id": fake.uuid4(),
            "gatewayToken": fake.sha256(),
            "cardType": random.choice(["VISA", "MASTERCARD", "PAYPAL", "AMEX"]),
            "last4Digits": str(fake.random_number(digits=4, fix_len=True)),
            "expiryDate": fake.credit_card_expire(start="now", end="+5y", date_format="%m/%y"),
            "cardHolderName": f"{first_name} {last_name}".upper()
        },
        "favoredPropertyIds": favored_ids
    }

def generate_manager():
    return {
        "id": fake.uuid4(),
        "username": fake.user_name(),
        "email": fake.email(),
        "password": fake.password(),
        "name": fake.first_name(),
        "surname": fake.last_name(),
        "phoneNumber": fake.phone_number(), 
        "iban": fake.iban(),
        "vatNumber": fake.bothify(text='??###########').upper(),        
        "billingAddress": {                 
            "street": fake.street_address(),
            "city": fake.city(),
            "zipCode": fake.postcode(),
            "country": fake.country(),
            "stateProvince": fake.state()
        }
    }

def generate_reservation():
    check_in = fake.date_between("-30d", "+30d")
    check_out = check_in + timedelta(days=random.randint(1, 7))
    return {
        "id": fake.uuid4(),
        "checkInDate": check_in.isoformat(),
        "checkOutDate": check_out.isoformat(),
        "creationDate": fake.date_between("-60d", check_in).isoformat(),
        "adults": random.randint(1, 4),
        "children": random.randint(0, 2),
        "status": "confirmed" 
    }

# POI coordinates (Monuments/Museums)
famous_places_coords = {
    "Rome":[
        {"name":"Colosseum","coordinates":[41.8902,12.4922], "type": "historical"},
        {"name":"Pantheon","coordinates":[41.8986,12.4768], "type": "historical"},
        {"name":"Roman Forum","coordinates":[41.8925,12.4853], "type": "historical"},
        {"name":"Piazza Navona","coordinates":[41.8992,12.4731], "type": "landmark"},
        {"name":"Vatican Museums","coordinates":[41.9029,12.4534], "type": "museum"}
    ],
    "Paris":[
        {"name":"Eiffel Tower","coordinates":[48.8584,2.2945], "type": "monument"},
        {"name":"Louvre Museum","coordinates":[48.8606,2.3376], "type": "museum"},
        {"name":"Notre-Dame Cathedral","coordinates":[48.8530,2.3499], "type": "historical"},
        {"name":"Montmartre","coordinates":[48.8867,2.3431], "type": "landmark"},
        {"name":"Musée d'Orsay","coordinates":[48.8600,2.3266], "type": "museum"}
    ],
    "London":[
        {"name":"Big Ben","coordinates":[51.5007,-0.1246], "type": "monument"},
        {"name":"Tower Bridge","coordinates":[51.5055,-0.0754], "type": "landmark"},
        {"name":"London Eye","coordinates":[51.5033,-0.1196], "type": "landmark"},
        {"name":"Buckingham Palace","coordinates":[51.5014,-0.1419], "type": "historical"},
        {"name":"British Museum","coordinates":[51.5194,-0.1270], "type": "museum"}
    ],
    "Berlin":[
        {"name":"Brandenburg Gate","coordinates":[52.5163,13.3777], "type": "monument"},
        {"name":"Reichstag Building","coordinates":[52.5186,13.3762], "type": "historical"},
        {"name":"Museum Island","coordinates":[52.5169,13.4010], "type": "museum"},
        {"name":"Checkpoint Charlie","coordinates":[52.5076,13.3904], "type": "historical"},
        {"name":"Alexanderplatz","coordinates":[52.5219,13.4132], "type": "landmark"}
    ],
    "Madrid":[
        {"name":"Plaza Mayor","coordinates":[40.4154,-3.7074], "type": "landmark"},
        {"name":"Prado Museum","coordinates":[40.4138,-3.6921], "type": "museum"},
        {"name":"Royal Palace of Madrid","coordinates":[40.4170,-3.7143], "type": "historical"},
        {"name":"El Retiro Park","coordinates":[40.4153,-3.6846], "type": "park"},
        {"name":"Puerta del Sol","coordinates":[40.4169,-3.7038], "type": "landmark"}
    ],
    "Lisbon":[
        {"name":"Belém Tower","coordinates":[38.6916,-9.2156], "type": "historical"},
        {"name":"Alfama District","coordinates":[38.7129,-9.1305], "type": "landmark"},
        {"name":"São Jorge Castle","coordinates":[38.7139,-9.1334], "type": "historical"},
        {"name":"Jerónimos Monastery","coordinates":[38.6971,-9.2065], "type": "historical"},
        {"name":"Commerce Square","coordinates":[38.7071,-9.1366], "type": "landmark"}
    ],
    "Vienna":[
        {"name":"Schönbrunn Palace","coordinates":[48.1845,16.3122], "type": "historical"},
        {"name":"St. Stephen's Cathedral","coordinates":[48.2082,16.3738], "type": "historical"},
        {"name":"Belvedere Palace","coordinates":[48.1915,16.3805], "type": "museum"},
        {"name":"Prater Park","coordinates":[48.2162,16.3989], "type": "park"},
        {"name":"Hofburg Imperial Palace","coordinates":[48.2060,16.3658], "type": "historical"}
    ],
    "Athens":[
        {"name":"Acropolis of Athens","coordinates":[37.9715,23.7257], "type": "historical"},
        {"name":"Parthenon","coordinates":[37.9715,23.7266], "type": "historical"},
        {"name":"Plaka District","coordinates":[37.9755,23.7346], "type": "landmark"},
        {"name":"Temple of Olympian Zeus","coordinates":[37.9699,23.7333], "type": "historical"},
        {"name":"National Archaeological Museum","coordinates":[37.9890,23.7330], "type": "museum"}
    ],
    "Budapest":[
        {"name":"Buda Castle","coordinates":[47.4969,19.0396], "type": "historical"},
        {"name":"Hungarian Parliament Building","coordinates":[47.5070,19.0450], "type": "landmark"},
        {"name":"Széchenyi Chain Bridge","coordinates":[47.4980,19.0396], "type": "landmark"},
        {"name":"Fisherman's Bastion","coordinates":[47.5020,19.0344], "type": "historical"},
        {"name":"Heroes' Square","coordinates":[47.5143,19.0773], "type": "landmark"}
    ],
    "Prague":[
        {"name":"Charles Bridge","coordinates":[50.0865,14.4114], "type": "landmark"},
        {"name":"Prague Castle","coordinates":[50.0903,14.3988], "type": "historical"},
        {"name":"Old Town Square","coordinates":[50.0870,14.4208], "type": "landmark"},
        {"name":"Prague Astronomical Clock","coordinates":[50.0871,14.4210], "type": "landmark"},
        {"name":"Wenceslas Square","coordinates":[50.0810,14.4265], "type": "landmark"}
    ],
    "Oslo":[
        {"name":"Oslo Opera House","coordinates":[59.9076,10.7532], "type": "landmark"},
        {"name":"Vigeland Park","coordinates":[59.9270,10.6983], "type": "park"},
        {"name":"Akershus Fortress","coordinates":[59.9078,10.7384], "type": "historical"},
        {"name":"Nobel Peace Center","coordinates":[59.9120,10.7382], "type": "museum"},
        {"name":"Karl Johans Gate","coordinates":[59.9122,10.7461], "type": "landmark"}
    ],
    "Copenhagen":[
        {"name":"Tivoli Gardens","coordinates":[55.6735,12.5681], "type": "park"},
        {"name":"Nyhavn","coordinates":[55.6803,12.5937], "type": "landmark"},
        {"name":"The Little Mermaid","coordinates":[55.6929,12.5994], "type": "monument"},
        {"name":"Rosenborg Castle","coordinates":[55.6850,12.5833], "type": "historical"},
        {"name":"Christiansborg Palace","coordinates":[55.6759,12.5831], "type": "historical"}
    ],
    "Stockholm":[
        {"name":"Gamla Stan (Old Town)","coordinates":[59.3250,18.0700], "type": "historical"},
        {"name":"Vasa Museum","coordinates":[59.3275,18.0916], "type": "museum"},
        {"name":"The Royal Palace","coordinates":[59.3276,18.0717], "type": "historical"},
        {"name":"ABBA The Museum","coordinates":[59.3277,18.0924], "type": "museum"},
        {"name":"Skansen Open-Air Museum","coordinates":[59.3270,18.0970], "type": "museum"}
    ]
}

# Real Dining & Nightlife Coordinates
dining_nightlife_coords = {
    "Rome": [
        {"name": "Salotto 42", "coordinates": [41.8995, 12.4786], "type": "bar"},
        {"name": "Roscioli Salumeria con Cucina", "coordinates": [41.8940, 12.4722], "type": "restaurant"},
        {"name": "The Jerry Thomas Project", "coordinates": [41.8973, 12.4716], "type": "bar"},
        {"name": "Antico Caffè Greco", "coordinates": [41.9056, 12.4823], "type": "cafe"}
    ],
    "Paris": [
        {"name": "Le Comptoir Général", "coordinates": [48.8716, 2.3662], "type": "bar"},
        {"name": "Café de Flore", "coordinates": [48.8541, 2.3326], "type": "cafe"},
        {"name": "Harry's New York Bar", "coordinates": [48.8698, 2.3314], "type": "bar"},
        {"name": "Le Relais de l'Entrecôte", "coordinates": [48.8710, 2.3013], "type": "restaurant"}
    ],
    "London": [
        {"name": "The Churchill Arms", "coordinates": [51.5073, -0.1960], "type": "pub"},
        {"name": "Sketch", "coordinates": [51.5126, -0.1413], "type": "restaurant"},
        {"name": "Gordon's Wine Bar", "coordinates": [51.5079, -0.1245], "type": "bar"},
        {"name": "Dishoom Covent Garden", "coordinates": [51.5133, -0.1265], "type": "restaurant"}
    ],
    "Berlin": [
        {"name": "Monkey Bar", "coordinates": [52.5057, 13.3364], "type": "bar"},
        {"name": "Hofbräu Wirtshaus Berlin", "coordinates": [52.5222, 13.4137], "type": "restaurant"},
        {"name": "Burgermeister Schlesisches Tor", "coordinates": [52.5013, 13.4419], "type": "restaurant"},
        {"name": "Berghain", "coordinates": [52.5111, 13.4431], "type": "club"}
    ],
    "Madrid": [
        {"name": "Chocolatería San Ginés", "coordinates": [40.4168, -3.7075], "type": "cafe"},
        {"name": "Mercado de San Miguel", "coordinates": [40.4155, -3.7090], "type": "restaurant"},
        {"name": "Salmon Guru", "coordinates": [40.4152, -3.6974], "type": "bar"},
        {"name": "Botín", "coordinates": [40.4147, -3.7073], "type": "restaurant"}
    ],
    "Lisbon": [
        {"name": "Pavilhão Chinês", "coordinates": [38.7153, -9.1465], "type": "bar"},
        {"name": "Time Out Market", "coordinates": [38.7071, -9.1462], "type": "restaurant"},
        {"name": "Park Bar", "coordinates": [38.7111, -9.1450], "type": "bar"},
        {"name": "Cervejaria Ramiro", "coordinates": [38.7214, -9.1338], "type": "restaurant"}
    ],
    "Vienna": [
        {"name": "Café Central", "coordinates": [48.2104, 16.3653], "type": "cafe"},
        {"name": "Figlmüller Wollzeile", "coordinates": [48.2093, 16.3752], "type": "restaurant"},
        {"name": "Loos American Bar", "coordinates": [48.2085, 16.3707], "type": "bar"},
        {"name": "Plachutta Wollzeile", "coordinates": [48.2089, 16.3791], "type": "restaurant"}
    ],
    "Athens": [
        {"name": "The Clumsies", "coordinates": [37.9785, 23.7297], "type": "bar"},
        {"name": "Baba Au Rum", "coordinates": [37.9774, 23.7314], "type": "bar"},
        {"name": "Brettos", "coordinates": [37.9723, 23.7296], "type": "bar"},
        {"name": "Karamanlidika", "coordinates": [37.9803, 23.7259], "type": "restaurant"}
    ],
    "Budapest": [
        {"name": "Szimpla Kert", "coordinates": [47.4971, 19.0638], "type": "bar"},
        {"name": "New York Café", "coordinates": [47.4987, 19.0700], "type": "cafe"},
        {"name": "Mazel Tov", "coordinates": [47.4988, 19.0658], "type": "restaurant"},
        {"name": "Instant-Fogas Complex", "coordinates": [47.5003, 19.0617], "type": "club"}
    ],
    "Prague": [
        {"name": "U Fleků", "coordinates": [50.0784, 14.4172], "type": "pub"},
        {"name": "Hemingway Bar", "coordinates": [50.0841, 14.4137], "type": "bar"},
        {"name": "Café Louvre", "coordinates": [50.0822, 14.4191], "type": "cafe"},
        {"name": "Lokál Dlouhááá", "coordinates": [50.0903, 14.4251], "type": "restaurant"}
    ],
    "Oslo": [
        {"name": "Himkok", "coordinates": [59.9149, 10.7497], "type": "bar"},
        {"name": "Mathallen Oslo", "coordinates": [59.9221, 10.7516], "type": "restaurant"},
        {"name": "Crow Bar & Brewery", "coordinates": [59.9175, 10.7558], "type": "pub"},
        {"name": "Fiskeriet Youngstorget", "coordinates": [59.9148, 10.7486], "type": "restaurant"}
    ],
    "Copenhagen": [
        {"name": "WarPigs", "coordinates": [55.6669, 12.5605], "type": "pub"},
        {"name": "Ruby", "coordinates": [55.6766, 12.5768], "type": "bar"},
        {"name": "Gasoline Grill", "coordinates": [55.6833, 12.5858], "type": "restaurant"},
        {"name": "Noma (approx)", "coordinates": [55.6828, 12.6102], "type": "restaurant"}
    ],
    "Stockholm": [
        {"name": "ICEBAR Stockholm", "coordinates": [59.3323, 18.0569], "type": "bar"},
        {"name": "Pelikan", "coordinates": [59.3116, 18.0817], "type": "restaurant"},
        {"name": "Pharmarium", "coordinates": [59.3250, 18.0706], "type": "bar"},
        {"name": "Fotografiska Restaurant", "coordinates": [59.3177, 18.0864], "type": "restaurant"}
    ]
}

def generate_pois(city, property_id, num_pois=6):
    city_places = famous_places_coords.get(city, [])
    city_dining = dining_nightlife_coords.get(city, [])
    
    # Combine lists and shuffle to get a mix of monuments and restaurants
    all_city_pois = city_places + city_dining
    random.shuffle(all_city_pois)
    
    pois = []
    for poi in all_city_pois[:num_pois]:
        pois.append({
            "id": fake.uuid4(),
            "property_id": property_id,
            "name": poi["name"],
            "coordinates": poi["coordinates"],
            "type": poi["type"]
        })
    return pois

def download_insideairbnb_csv(file_url):
    try:
        r = requests.get(file_url, timeout=20)
        r.raise_for_status()
        with gzip.open(io.BytesIO(r.content), mode='rt', encoding='utf-8') as f:
            reader = list(csv.DictReader(f))
        return reader
    except Exception as e:
        print(f"Error downloading CSV {file_url}: {e}")
        return []

# Notification Helper
def create_notification(recipient_id, title, body, type, ref_id, timestamp):
    return {
        "id": fake.uuid4(),
        "recipientId": recipient_id,
        "title": title,
        "body": body,
        "type": type,
        "referenceId": ref_id,
        "read": random.choice([True, False]),
        "timestamp": timestamp
    }

# Dataset generation

def generate_dataset(listings_csv_url, reviews_csv_url, city, country, region, entities, max_properties=150):
    listings = download_insideairbnb_csv(listings_csv_url)
    reviews_csv = download_insideairbnb_csv(reviews_csv_url)

    # Helper to track property IDs in this batch for favoredPropertyIds
    generated_property_ids = []

    for row in listings[:max_properties]:
        property_id = fake.uuid4()
        generated_property_ids.append(property_id)

        amenities = row.get("amenities", "").replace("{","").replace("}","").split(",")
        if amenities == [""]:
            amenities = [fake.word() for _ in range(5)]

        lat = float(row.get("latitude", 0))
        lon = float(row.get("longitude", 0))
        
        city_name = city 

        # Manager
        manager = generate_manager()
        entities["managers"].append(manager)

        # Property photos
        property_photos = [fake.image_url() for _ in range(5)]

        # Property Base
        property_data = {
            "id": property_id,
            "name": row.get("name") or fake.company(),
            "address": row.get("neighbourhood_cleansed") or fake.address(),
            "description": row.get("description") or fake.text(max_nb_chars=500),
            "amenities": amenities,
            "photos": property_photos,
            "email": fake.company_email(),
            "country": country,
            "region": region,
            "city": city_name,
            "manager_id": manager["id"],
            "coordinates": [lat, lon],
            "ratingStats": {
                "cleanliness": 0.0,
                "communication": 0.0,
                "location": 0.0,
                "value": 0.0
            },
            "latestReviews": [] 
        }
        
        # Rooms
        rooms_for_property = []
        for i in range(random.randint(3,5)):
            room_photos = [fake.image_url() for _ in range(random.randint(3,5))]
            room = {
                "id": fake.uuid4(),
                "property_id": property_id,
                "roomType": random.choice(["single","matrimonial","double","suite"]),
                "amenities": amenities,
                "name": f"Room {i+1}",
                "beds": random.randint(1,3),
                "photos": room_photos,
                "status": "available",
                "capacityAdults": random.randint(1,4),
                "capacityChildren": random.randint(0,3),
                "pricePerNightAdults": round(random.uniform(30,200),2),
                "pricePerNightChildren": round(random.uniform(10,50),2)
            }
            entities["rooms"].append(room)
            rooms_for_property.append(room)

        # Containers for rating calculation and latest reviews
        cleanliness_sum = 0
        communication_sum = 0
        location_sum = 0
        value_sum = 0
        review_count = 0
        property_specific_reviews = []
        
        # Track customers created for this property to assign reviews to them
        property_customers = []
        # Track reservations for this property to link reviews
        property_reservations = []

        # Customers & Reservations & Messages & Notifications
        for _ in range(random.randint(10,20)):
            customer = generate_customer(generated_property_ids)
            if random.random() > 0.7:
                 if property_id not in customer["favoredPropertyIds"]:
                     customer["favoredPropertyIds"].append(property_id)
            
            entities["customers"].append(customer)
            property_customers.append(customer) 

            reservation = generate_reservation()
            reservation["room_id"] = random.choice(rooms_for_property)["id"] 
            reservation["customer_id"] = customer["id"]
            
            # Store reservation locally to link it to reviews later
            property_reservations.append(reservation)
            
            # Advanced Notification Logic
            res_event = random.random()
            
            if res_event > 0.9: 
                # Case: CANCELLED
                reservation["status"] = "cancelled"
                entities["reservations"].append(reservation) 
                
                # 1. Created
                entities["notifications"].append(create_notification(
                    recipient_id=manager["id"], title="New Booking",
                    body=f"Customer {customer['name']} has made a new reservation.",
                    type="RESERVATION_CREATED", ref_id=reservation["id"],
                    timestamp=reservation["creationDate"]
                ))
                
                # 2. Cancelled
                cancel_time = datetime.fromisoformat(reservation["creationDate"]) + timedelta(hours=random.randint(1, 24))
                entities["notifications"].append(create_notification(
                    recipient_id=manager["id"], title="Booking Cancelled",
                    body=f"Customer {customer['name']} has cancelled their reservation.",
                    type="RESERVATION_CANCELLED", ref_id=reservation["id"],
                    timestamp=cancel_time.isoformat()
                ))

            elif res_event > 0.8:
                # Case: MODIFIED
                entities["reservations"].append(reservation)
                
                # 1. Created
                entities["notifications"].append(create_notification(
                    recipient_id=manager["id"], title="New Booking",
                    body=f"Customer {customer['name']} has made a new reservation.",
                    type="RESERVATION_CREATED", ref_id=reservation["id"],
                    timestamp=reservation["creationDate"]
                ))
                
                # 2. Modified
                mod_time = datetime.fromisoformat(reservation["creationDate"]) + timedelta(hours=random.randint(1, 12))
                entities["notifications"].append(create_notification(
                    recipient_id=manager["id"], title="Booking Modified",
                    body=f"Customer {customer['name']} modified their reservation details.",
                    type="RESERVATION_MODIFIED", ref_id=reservation["id"],
                    timestamp=mod_time.isoformat()
                ))
                
            else:
                # Case: STANDARD
                entities["reservations"].append(reservation)
                entities["notifications"].append(create_notification(
                    recipient_id=manager["id"], title="New Booking",
                    body=f"Customer {customer['name']} has made a new reservation.",
                    type="RESERVATION_CREATED", ref_id=reservation["id"],
                    timestamp=reservation["creationDate"]
                ))

            # Messages
            if random.random() > 0.5:
                # 1. Customer sends message
                msg_time_1 = datetime.fromisoformat(reservation["creationDate"]) + timedelta(minutes=random.randint(10, 120))
                msg1 = {
                    "id": fake.uuid4(),
                    "sender_id": customer["id"],
                    "recipient_id": manager["id"],
                    "timestamp": msg_time_1.isoformat(),
                    "content": fake.sentence(nb_words=10),
                    "is_read": True
                }
                entities["messages"].append(msg1)
                
                entities["notifications"].append(create_notification(
                    recipient_id=manager["id"],
                    title="New Message",
                    body=f"User {customer['username']} wrote to you",
                    type="MESSAGE",
                    ref_id=msg1["id"],
                    timestamp=msg1["timestamp"]
                ))

                # 2. Manager replies
                msg_time_2 = msg_time_1 + timedelta(minutes=random.randint(5, 60))
                msg2 = {
                    "id": fake.uuid4(),
                    "sender_id": manager["id"],
                    "recipient_id": customer["id"],
                    "timestamp": msg_time_2.isoformat(),
                    "content": fake.sentence(nb_words=12),
                    "is_read": random.choice([True, False])
                }
                entities["messages"].append(msg2)

                entities["notifications"].append(create_notification(
                    recipient_id=customer["id"],
                    title="New Message",
                    body=f"Manager wrote to you",
                    type="MESSAGE",
                    ref_id=msg2["id"],
                    timestamp=msg2["timestamp"]
                ))

        # Reviews
        for row_rev in reviews_csv[:50]:
            clean_score = random.randint(3, 5)
            comm_score = random.randint(3, 5)
            loc_score = random.randint(3, 5)
            val_score = random.randint(3, 5)

            cleanliness_sum += clean_score
            communication_sum += comm_score
            location_sum += loc_score
            value_sum += val_score
            review_count += 1
            
            # Select a random reservation if available to link the review
            reviewer_id = fake.uuid4()
            reservation_id = fake.uuid4() # Fallback

            if property_reservations:
                # Pick a random reservation from this property
                selected_res = random.choice(property_reservations)
                reservation_id = selected_res["id"]
                # Ensure the reviewer is the person who made the reservation
                reviewer_id = selected_res["customer_id"]
            elif property_customers:
                # Fallback if no reservations found but customers exist (rare)
                reviewer_id = random.choice(property_customers)["id"]

            review = {
                "id": fake.uuid4(),
                "propertyId": property_id, 
                "userId": reviewer_id,     
                "reservationId": reservation_id,
                "creationDate": row_rev.get("date","") or fake.date_between("-1y","today").isoformat(),
                "text": row_rev.get("comments") or fake.text(max_nb_chars=300),
                "rating": random.randint(3,5),
                "cleanliness": float(clean_score),
                "communication": float(comm_score),
                "location": float(loc_score),
                "value": float(val_score),
                "managerReply": fake.sentence() if random.choice([True,False]) else None
            }
            entities["reviews"].append(review)
            property_specific_reviews.append(review)
        
        # Calculate Property Average Ratings and populate latestReviews
        if review_count > 0:
            property_data["ratingStats"]["cleanliness"] = round(cleanliness_sum / review_count, 1)
            property_data["ratingStats"]["communication"] = round(communication_sum / review_count, 1)
            property_data["ratingStats"]["location"] = round(location_sum / review_count, 1)
            property_data["ratingStats"]["value"] = round(value_sum / review_count, 1)
            
            # Sort reviews by date descending (newest first)
            property_specific_reviews.sort(key=lambda r: r['creationDate'], reverse=True)
            
            # Take the top 10 and add to property
            property_data["latestReviews"] = property_specific_reviews[:10]

        # Add property to entities list
        entities["properties"].append(property_data)

        # POIs
        entities.setdefault("pois",[])
        entities["pois"].extend(generate_pois(city_name, property_id))

    return entities

# Main execution

def main():
    city_data = {
        "Rome": {
            "country": "Italy", "region": "Lazio",
            "listings":"https://data.insideairbnb.com/italy/lazio/rome/2025-09-14/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/italy/lazio/rome/2025-09-14/data/reviews.csv.gz"
        },
        "Paris": {
            "country": "France", "region": "Île-de-France",
            "listings":"https://data.insideairbnb.com/france/ile-de-france/paris/2025-09-12/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/france/ile-de-france/paris/2025-09-12/data/reviews.csv.gz"
        },
        "London": {
            "country": "United Kingdom", "region": "England",
            "listings":"https://data.insideairbnb.com/united-kingdom/england/london/2025-09-14/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/united-kingdom/england/london/2025-09-14/data/reviews.csv.gz"
        },
        "Berlin": {
            "country": "Germany", "region": "Berlin",
            "listings":"https://data.insideairbnb.com/germany/be/berlin/2025-09-23/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/germany/be/berlin/2025-09-23/data/reviews.csv.gz"
        },
        "Madrid": {
            "country": "Spain", "region": "Community of Madrid",
            "listings":"https://data.insideairbnb.com/spain/comunidad-de-madrid/madrid/2025-09-14/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/spain/comunidad-de-madrid/madrid/2025-09-14/data/reviews.csv.gz"
        },
        "Lisbon": {
            "country": "Portugal", "region": "Lisbon",
            "listings":"https://data.insideairbnb.com/portugal/lisbon/lisbon/2025-09-21/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/portugal/lisbon/lisbon/2025-09-21/data/reviews.csv.gz"
        },
        "Vienna": {
            "country": "Austria", "region": "Vienna",
            "listings":"https://data.insideairbnb.com/austria/vienna/vienna/2025-09-14/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/austria/vienna/vienna/2025-09-14/data/reviews.csv.gz"
        },
        "Athens": {
            "country": "Greece", "region": "Attica",
            "listings":"https://data.insideairbnb.com/greece/attica/athens/2025-09-26/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/greece/attica/athens/2025-09-26/data/reviews.csv.gz"
        },
        "Budapest": {
            "country": "Hungary", "region": "Central Hungary",
            "listings":"https://data.insideairbnb.com/hungary/k%C3%B6z%C3%A9p-magyarorsz%C3%A1g/budapest/2025-09-25/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/hungary/k%C3%B6z%C3%A9p-magyarorsz%C3%A1g/budapest/2025-09-25/data/reviews.csv.gz"
        },
        "Prague": {
            "country": "Czech Republic", "region": "Prague",
            "listings":"https://data.insideairbnb.com/czech-republic/prague/prague/2025-09-23/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/czech-republic/prague/prague/2025-09-23/data/reviews.csv.gz"
        },
        "Oslo": {
            "country": "Norway", "region": "Oslo",
            "listings":"https://data.insideairbnb.com/norway/oslo/oslo/2025-09-29/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/norway/oslo/oslo/2025-09-29/data/reviews.csv.gz"
        },
        "Copenhagen": {
            "country": "Denmark", "region": "Capital Region of Denmark",
            "listings":"https://data.insideairbnb.com/denmark/hovedstaden/copenhagen/2025-09-29/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/denmark/hovedstaden/copenhagen/2025-09-29/data/reviews.csv.gz"
        },
        "Stockholm": {
            "country": "Sweden", "region": "Stockholm County",
            "listings":"https://data.insideairbnb.com/sweden/stockholms-l%C3%A4n/stockholm/2025-09-29/data/listings.csv.gz",
            "reviews":"https://data.insideairbnb.com/sweden/stockholms-l%C3%A4n/stockholm/2025-09-29/data/reviews.csv.gz"
        }
    }

    entities = {
        "customers": [],
        "managers": [],
        "properties": [],
        "rooms": [],
        "reservations": [],
        "reviews": [],
        "pois": [],
        "messages": [],
        "notifications": []
    }

    for city, data in city_data.items():
        print(f"Generating data for {city}...")
        generate_dataset(
            data["listings"], 
            data["reviews"], 
            city, 
            data["country"], 
            data["region"], 
            entities, 
            max_properties=150
        )

    for entity_name, data in entities.items():
        with open(f"output_entities/{entity_name}.json","w",encoding="utf-8") as f:
            json.dump(data,f,indent=2,ensure_ascii=False)

    print("All datasets created successfully.")

if __name__ == "__main__":
    main()