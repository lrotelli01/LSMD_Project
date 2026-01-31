import json
from neo4j import GraphDatabase
import os
# --- NEO4J CONFIGURATION ---
# Change the password if you have changed it
URI = "neo4j://10.1.1.23:7687"
AUTH = ("neo4j", "Carota123!") 
base_path = os.path.dirname(os.path.abspath(__file__))
def load_json(filename):
    try: 
        file_path = os.path.join(base_path, filename)
        with open(file_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"ERROR: File {file_path} not found.")
        return []

class LargeBnBImporter:
    def __init__(self, uri, auth):
        self.driver = GraphDatabase.driver(uri, auth=auth)

    def close(self):
        self.driver.close()

    def create_constraints(self):
        """Creates uniqueness constraints to speed up import and avoid duplicates"""
        print("Creating constraints...")
        with self.driver.session() as session:
            # Username Constraint on User (primary key in Java model)
            session.run("CREATE CONSTRAINT user_username IF NOT EXISTS FOR (u:User) REQUIRE u.username IS UNIQUE")
            # ID Constraint on Property
            session.run("CREATE CONSTRAINT property_id IF NOT EXISTS FOR (p:Property) REQUIRE p.propertyId IS UNIQUE")
            # Amenity name constraint
            session.run("CREATE CONSTRAINT amenity_name IF NOT EXISTS FOR (a:Amenity) REQUIRE a.name IS UNIQUE")

    def import_users(self, customers, managers):
        """Imports Users (Customers and Managers)"""
        print("Importing Users...")
        query = """
        UNWIND $batch AS row
        MERGE (u:User {username: row.username})
        SET u.mongoId = row.id,
            u.userId = row.id
        """
        
        # Merge the two lists adding the role
        all_users = []
        for c in customers:
            all_users.append({
                "id": c['id'], 
                "username": c.get('username', c['id'])  # Use username from JSON
            })
        for m in managers:
            all_users.append({
                "id": m['id'], 
                "username": m.get('username', m['id'])  # Use username from JSON
            })

        # Execute in batches of 1000 to avoid clogging memory
        batch_size = 1000
        with self.driver.session() as session:
            for i in range(0, len(all_users), batch_size):
                batch = all_users[i:i+batch_size]
                session.run(query, batch=batch)
                print(f"   - Processed {i + len(batch)} users...")

    def import_properties(self, properties):
        """Imports Properties"""
        print("Importing Properties...")
        query = """
        UNWIND $batch AS row
        MERGE (p:Property {propertyId: row.id})
        """
        
        data = []
        for p in properties:
            data.append({"id": p['id']})

        batch_size = 1000
        with self.driver.session() as session:
            for i in range(0, len(data), batch_size):
                batch = data[i:i+batch_size]
                session.run(query, batch=batch)
                print(f"   - Processed {i + len(batch)} properties...")

    def import_reservations(self, reservations, rooms):
        """
        Creates relationships (:User)-[:BOOKED]->(:Property).
        Since reservations have room_id, we use rooms.json to find the property_id.
        """
        print("Mapping Rooms to Properties...")
        # Create a fast map: RoomID -> PropertyID
        room_to_prop = {r['id']: r['property_id'] for r in rooms if 'property_id' in r}
        
        print("Importing BOOKED relationships...")
        query = """
        UNWIND $batch AS row
        MATCH (u:User {mongoId: row.userId})
        MATCH (p:Property {propertyId: row.propertyId})
        MERGE (u)-[:BOOKED {date: date(row.date)}]->(p)
        """
        
        rels = []
        for res in reservations:
            r_id = res.get('room_id')
            u_id = res.get('customer_id') or res.get('userId') # Handles various formats
            
            # Find the property associated with the room
            p_id = room_to_prop.get(r_id)
            
            if p_id and u_id:
                # Take the date to use in queries (e.g. "recent bookings")
                date_str = res.get('checkInDate', '2024-01-01')[:10] # Take only YYYY-MM-DD
                
                rels.append({
                    "userId": u_id,
                    "propertyId": p_id,
                    "date": date_str
                })

        batch_size = 1000
        with self.driver.session() as session:
            for i in range(0, len(rels), batch_size):
                batch = rels[i:i+batch_size]
                session.run(query, batch=batch)
                print(f"   - Processed {i + len(batch)} relationships...")
    
    def create_additional_bookings(self, properties):
        """
        Creates additional BOOKED relationships to enable collaborative filtering.
        Randomly assigns 1-2 extra properties to 10% of users.
        """
        import random
        
        print("Creating additional bookings for collaborative filtering...")
        
        # Get all user IDs from Neo4j
        with self.driver.session() as session:
            result = session.run("MATCH (u:User) RETURN u.mongoId as userId")
            user_ids = [record['userId'] for record in result]
        
        # Select 10% of users to get additional bookings (reduced from 30%)
        users_to_boost = random.sample(user_ids, int(len(user_ids) * 0.10))
        
        # Get random property IDs
        property_ids = [p['id'] for p in properties if 'id' in p]
        
        query = """
        UNWIND $batch AS row
        MATCH (u:User {mongoId: row.userId})
        MATCH (p:Property {propertyId: row.propertyId})
        MERGE (u)-[:BOOKED {date: date(row.date)}]->(p)
        """
        
        additional_rels = []
        for user_id in users_to_boost:
            # Each user gets 1-2 additional random bookings (reduced from 2-5)
            num_bookings = random.randint(1, 2)
            selected_props = random.sample(property_ids, min(num_bookings, len(property_ids)))
            
            for prop_id in selected_props:
                # Random date in 2024
                month = random.randint(1, 12)
                day = random.randint(1, 28)
                date_str = f"2024-{month:02d}-{day:02d}"
                
                additional_rels.append({
                    "userId": user_id,
                    "propertyId": prop_id,
                    "date": date_str
                })
        
        batch_size = 1000
        with self.driver.session() as session:
            for i in range(0, len(additional_rels), batch_size):
                batch = additional_rels[i:i+batch_size]
                session.run(query, batch=batch)
                print(f"   - Created {i + len(batch)} additional bookings...")
        
        print(f"   - Total additional bookings created: {len(additional_rels)}")

    def import_amenities(self, properties):
        """
        Creates Amenity nodes and [:HAS] relationships for content-based filtering.
        Extracts all unique amenities from properties and links them.
        """
        print("Importing Amenities and HAS relationships...")
        
        # Step 1: Collect all unique amenities
        all_amenities = set()
        for prop in properties:
            amenities = prop.get('amenities', [])
            if amenities:
                all_amenities.update(amenities)
        
        print(f"   - Found {len(all_amenities)} unique amenities")
        
        # Step 2: Create Amenity nodes
        amenity_query = """
        UNWIND $batch AS amenityName
        MERGE (a:Amenity {name: amenityName})
        """
        
        with self.driver.session() as session:
            session.run(amenity_query, batch=list(all_amenities))
            print(f"   - Created {len(all_amenities)} Amenity nodes")
        
        # Step 3: Create HAS relationships
        has_query = """
        UNWIND $batch AS row
        MATCH (p:Property {propertyId: row.propertyId})
        UNWIND row.amenities AS amenityName
        MATCH (a:Amenity {name: amenityName})
        MERGE (p)-[:HAS]->(a)
        """
        
        data = []
        for prop in properties:
            amenities = prop.get('amenities', [])
            if amenities:
                data.append({
                    "propertyId": prop['id'],
                    "amenities": amenities
                })
        
        batch_size = 500  # Smaller batches because of UNWIND inside UNWIND
        with self.driver.session() as session:
            for i in range(0, len(data), batch_size):
                batch = data[i:i+batch_size]
                session.run(has_query, batch=batch)
                print(f"   - Processed HAS relationships for {i + len(batch)} properties...")

def main():
    print("--- STARTING NEO4J IMPORT ---")
    
    # 1. Load JSONs
    customers = load_json('customers.json')
    managers = load_json('managers.json')
    properties = load_json('properties.json')
    rooms = load_json('rooms.json')
    reservations = load_json('reservations.json')

    if not properties:
        print("Missing data. Check JSON files.")
        return

    # 2. Connection to DB
    importer = LargeBnBImporter(URI, AUTH)
    
    try:
        # 3. Create Constraints (Important!)
        importer.create_constraints()
        
        # 4. Import Nodes
        importer.import_users(customers, managers)
        importer.import_properties(properties)
        
        # 5. Import Relationships
        importer.import_reservations(reservations, rooms)
        
        # 5b. Create additional bookings for collaborative filtering
        importer.create_additional_bookings(properties)
        
        # 6. Import Amenities and HAS relationships (for content-based filtering)
        importer.import_amenities(properties)
        
    except Exception as e:
        print(f"CRITICAL ERROR: {e}")
    finally:
        importer.close()
        print("--- IMPORT COMPLETED ---")

if __name__ == "__main__":
    main()  