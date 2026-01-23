import json
from neo4j import GraphDatabase

# --- NEO4J CONFIGURATION ---
# Change the password if you have changed it!
URI = "bolt://localhost:7687"
AUTH = ("neo4j", "Carota123!") 

def load_json(filename):
    try:
        with open(filename, 'r', encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"ERROR: File {filename} not found.")
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
            # ID Constraint on User
            session.run("CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.userId IS UNIQUE")
            # ID Constraint on Property
            session.run("CREATE CONSTRAINT property_id IF NOT EXISTS FOR (p:Property) REQUIRE p.propertyId IS UNIQUE")

    def import_users(self, customers, managers):
        """Imports Users (Customers and Managers)"""
        print("Importing Users...")
        query = """
        UNWIND $batch AS row
        MERGE (u:User {userId: row.id})
        SET u.name = row.name, u.role = row.role
        """
        
        # Merge the two lists adding the role
        all_users = []
        for c in customers:
            all_users.append({"id": c['id'], "name": c.get('name', 'Unknown'), "role": "CUSTOMER"})
        for m in managers:
            all_users.append({"id": m['id'], "name": m.get('name', 'Unknown'), "role": "MANAGER"})

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
        SET p.name = row.name, p.city = row.city
        """
        
        data = []
        for p in properties:
            # Extract city if it exists (useful for future queries)
            city = p.get('location', {}).get('city', 'Unknown') 
            # If in the original JSON 'city' is outside 'location', adapt here:
            if 'city' in p: city = p['city']
            
            data.append({"id": p['id'], "name": p.get('name', 'Property'), "city": city})

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
        MATCH (u:User {userId: row.userId})
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
        
        # 5. Import Relationships (using room mapping)
        importer.import_reservations(reservations, rooms)
        
    except Exception as e:
        print(f"CRITICAL ERROR: {e}")
    finally:
        importer.close()
        print("--- IMPORT COMPLETED ---")

if __name__ == "__main__":
    main()