import json
import pymongo
from datetime import datetime
import os

# --- CONFIGURATION ---
MONGO_URI = "mongodb://localhost:27017/"
DB_NAME = "large_bnb_db"

# FOLDER PATH
# NOW SET TO: The same directory as this script file.
# It will look for 'properties.json', 'rooms.json', etc. right next to this .py file.
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

def load_json(filename):
    """Loads a JSON file from the BASE_DIR."""
    full_path = os.path.join(BASE_DIR, filename)
    try:
        with open(full_path, 'r', encoding='utf-8') as f:
            print(f"Loading {filename}...")
            return json.load(f)
    except FileNotFoundError:
        print(f"WARNING: Cannot find file {filename} in {BASE_DIR}. Skipping.")
        return []

def clean_amenities(amenities_field):
    """
    Ensures amenities are a clean list of strings.
    """
    if isinstance(amenities_field, list):
        return amenities_field
    
    if isinstance(amenities_field, str):
        try:
            cleaned = amenities_field.replace('{', '').replace('}', '').replace('[', '').replace(']', '').replace('"', '')
            return [x.strip() for x in cleaned.split(',') if x.strip()]
        except:
            return []
    return []

def main():
    client = pymongo.MongoClient(MONGO_URI)
    db = client[DB_NAME]
    
    print(f"Target Database: {DB_NAME}")
    print(f"Reading data from directory: {BASE_DIR}")

    print("\n--- 1. LOADING DATASETS ---")
    raw_properties = load_json('properties.json')
    raw_rooms = load_json('rooms.json')
    raw_reviews = load_json('reviews.json')
    raw_customers = load_json('customers.json')
    raw_managers = load_json('managers.json')
    raw_reservations = load_json('reservations.json')
    raw_messages = load_json('messages.json')
    raw_notifications = load_json('notifications.json')
    raw_pois = load_json('pois.json')
    
    if not raw_properties: 
        print("CRITICAL: properties.json missing or empty. Exiting.")
        return

    print("\n--- 2. PROCESSING USERS (Customers & Managers) ---")
    users_collection = []
    seen_emails = set()
    
    # Process Customers
    for c in raw_customers:
        if c['email'] in seen_emails: continue
        seen_emails.add(c['email'])
        
        # Rename id -> _id
        c['_id'] = c.pop('id')
        c['role'] = 'CUSTOMER'
        
        # Ensure dates are datetime objects
        if 'birthdate' in c:
            try:
                c['birthdate'] = datetime.fromisoformat(c['birthdate'])
            except: pass
        
        # favoredPropertyIds is already a list of strings from generator
        if 'favoredPropertyIds' not in c:
            c['favoredPropertyIds'] = []

        users_collection.append(c)
        
    # Process Managers
    for m in raw_managers:
        if m['email'] in seen_emails: continue
        seen_emails.add(m['email'])
        
        m['_id'] = m.pop('id')
        m['role'] = 'MANAGER'
        users_collection.append(m)
    
    # Bulk Insert Users
    db.users.delete_many({}) 
    if users_collection:
        db.users.insert_many(users_collection)
    print(f"Inserted {len(users_collection)} unique users.")

    print("\n--- 3. PROCESSING PROPERTIES (Embedding Rooms, POIs, Stats) ---")
    
    # 3a. Map Rooms to Property ID
    rooms_by_property = {}
    for r in raw_rooms:
        pid = r.get('property_id')
        if not pid: continue
        if pid not in rooms_by_property: rooms_by_property[pid] = []
        
        r_copy = r.copy()
        r_copy['roomId'] = r_copy.pop('id')
        if 'property_id' in r_copy: del r_copy['property_id']
        rooms_by_property[pid].append(r_copy)

    # 3b. Map POIs to Property ID
    pois_by_property = {}
    for poi in raw_pois:
        pid = poi.get('property_id')
        if not pid: continue
        if pid not in pois_by_property: pois_by_property[pid] = []
        
        poi_copy = poi.copy()
        if 'property_id' in poi_copy: del poi_copy['property_id']
        pois_by_property[pid].append(poi_copy)

    properties_final = []

    for prop in raw_properties:
        p_id = prop['id']
        new_prop = prop.copy()
        new_prop['_id'] = p_id
        if 'id' in new_prop: del new_prop['id']
        
        # Rename manager_id -> managerId for consistency
        if 'manager_id' in new_prop:
            new_prop['managerId'] = new_prop.pop('manager_id')

        # Clean Amenities
        new_prop['amenities'] = clean_amenities(new_prop.get('amenities', []))

        # GEOJSON CONVERSION
        # Generator gave [Lat, Lon]. MongoDB requires [Lon, Lat].
        if 'coordinates' in new_prop and len(new_prop['coordinates']) == 2:
            lat, lon = new_prop['coordinates']
            new_prop['location'] = {
                "type": "Point",
                "coordinates": [lon, lat] 
            }
            del new_prop['coordinates'] 

        # Embed Rooms
        new_prop['rooms'] = rooms_by_property.get(p_id, [])
        
        # Embed POIs
        new_prop['pois'] = pois_by_property.get(p_id, [])

        # Ensure ratingStats exists
        if 'ratingStats' not in new_prop:
            new_prop['ratingStats'] = {
                "cleanliness": 0.0, "communication": 0.0,
                "location": 0.0, "value": 0.0
            }

        properties_final.append(new_prop)

    # Insert Properties
    db.properties.delete_many({})
    if properties_final:
        db.properties.insert_many(properties_final)
        # Create Geospatial Index
        db.properties.create_index([("location", "2dsphere")])
    print(f"Inserted {len(properties_final)} properties.")

    print("\n--- 4. PROCESSING REVIEWS ---")
    reviews_final_collection = []
    
    for rv in raw_reviews:
        rv_doc = rv.copy()
        rv_doc['_id'] = rv_doc.pop('id')
        
        # CamelCase consistency
        if 'property_id' in rv_doc: 
            rv_doc['propertyId'] = rv_doc.pop('property_id')
            
        # Date Parsing
        try:
            rv_doc['creationDate'] = datetime.fromisoformat(rv_doc['creationDate'])
        except:
            rv_doc['creationDate'] = datetime.now()
        
        # Ensure numeric fields
        for key in ['cleanliness', 'communication', 'location', 'value', 'rating']:
            if key in rv_doc:
                rv_doc[key] = float(rv_doc[key])

        reviews_final_collection.append(rv_doc)

    # Insert Reviews
    db.reviews.delete_many({})
    if reviews_final_collection:
        db.reviews.insert_many(reviews_final_collection)
    print(f"Inserted {len(reviews_final_collection)} reviews.")

    print("\n--- 5. PROCESSING RESERVATIONS ---")
    reservations_final = []
    for res in raw_reservations:
        res_doc = res.copy()
        res_doc['_id'] = res_doc.pop('id')
        res_doc['userId'] = res_doc.pop('customer_id')
        res_doc['roomId'] = res_doc.pop('room_id')
        
        # Date Parsing
        try:
            res_doc['dates'] = {
                "checkIn": datetime.fromisoformat(res_doc['checkInDate']),
                "checkOut": datetime.fromisoformat(res_doc['checkOutDate'])
            }
            res_doc['createdAt'] = datetime.fromisoformat(res_doc['creationDate'])
        except:
            pass
            
        # Cleanup old date string fields
        for k in ['checkInDate', 'checkOutDate', 'creationDate']:
            if k in res_doc: del res_doc[k]
            
        reservations_final.append(res_doc)

    db.reservations.delete_many({})
    if reservations_final:
        db.reservations.insert_many(reservations_final)
    print(f"Inserted {len(reservations_final)} reservations.")

    print("\n--- 6. PROCESSING MESSAGES ---")
    messages_final = []
    for msg in raw_messages:
        msg_doc = msg.copy()
        msg_doc['_id'] = msg_doc.pop('id')
        
        # CamelCase consistency
        if 'sender_id' in msg_doc: msg_doc['senderId'] = msg_doc.pop('sender_id')
        if 'recipient_id' in msg_doc: msg_doc['recipientId'] = msg_doc.pop('recipient_id')
        if 'is_read' in msg_doc: msg_doc['isRead'] = msg_doc.pop('is_read')
        
        try:
            msg_doc['timestamp'] = datetime.fromisoformat(msg_doc['timestamp'])
        except:
            msg_doc['timestamp'] = datetime.now()
            
        messages_final.append(msg_doc)

    db.messages.delete_many({})
    if messages_final:
        db.messages.insert_many(messages_final)
    print(f"Inserted {len(messages_final)} messages.")

    print("\n--- 7. PROCESSING NOTIFICATIONS ---")
    notifications_final = []
    for notif in raw_notifications:
        n_doc = notif.copy()
        n_doc['_id'] = n_doc.pop('id')
        
        # Date Parsing
        try:
            n_doc['timestamp'] = datetime.fromisoformat(n_doc['timestamp'])
        except:
            n_doc['timestamp'] = datetime.now()
            
        notifications_final.append(n_doc)
        
    db.notifications.delete_many({})
    if notifications_final:
        db.notifications.insert_many(notifications_final)
    print(f"Inserted {len(notifications_final)} notifications.")

    print("\n--- DONE! Database populated successfully. ---")

if __name__ == "__main__":
    main()