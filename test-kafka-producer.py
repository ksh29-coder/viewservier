#!/usr/bin/env python3
"""
Simple Kafka producer to test grid updates
Sends periodic cell updates to demonstrate ongoing changes
"""

import json
import time
import random
from kafka import KafkaProducer
from datetime import datetime

def create_producer():
    """Create Kafka producer"""
    return KafkaProducer(
        bootstrap_servers=['localhost:9092'],
        value_serializer=lambda v: json.dumps(v).encode('utf-8'),
        key_serializer=lambda k: k.encode('utf-8') if k else None
    )

def create_cell_update_message(grid_id="user1_view1", num_changes=1):
    """Create a grid update message with random cell changes"""
    changes = []
    
    for i in range(num_changes):
        # Generate random cell position within our 50x25 grid
        row = random.randint(0, 49)  # 0-49 for 50 rows  
        col = random.randint(0, 24)  # 0-24 for 25 columns
        
        # Generate random value
        value_types = ["string", "number", "integer", "boolean", "timestamp"]
        data_type = random.choice(value_types)
        
        if data_type == "string":
            value = f"Updated_{random.randint(1000, 9999)}"
        elif data_type == "number":
            value = round(random.uniform(1.0, 1000.0), 2)
        elif data_type == "integer":
            value = random.randint(1, 1000)
        elif data_type == "boolean":
            value = random.choice([True, False])
        else:  # timestamp
            value = int(time.time() * 1000)
        
        changes.append({
            "row": row,
            "column": col,
            "newValue": value,
            "dataType": data_type
        })
    
    return {
        "batchId": f"batch_{int(time.time() * 1000)}_{random.randint(1000, 9999)}",
        "gridId": grid_id,
        "eventType": "CELL_UPDATE",
        "changes": changes,
        "timestamp": int(time.time() * 1000)
    }

def main():
    print("🚀 Starting Kafka producer for grid updates...")
    print("Will send updates to grid: user1_view1")
    print("Press Ctrl+C to stop\n")
    
    try:
        producer = create_producer()
        print("✅ Connected to Kafka")
        
        batch_count = 1
        
        while True:
            # Create and send update message
            message = create_cell_update_message(num_changes=random.randint(1, 3))
            
            producer.send('grid-updates', value=message, key='user1_view1')
            producer.flush()
            
            print(f"📤 Sent batch #{batch_count}: {len(message['changes'])} cell updates")
            print(f"   Grid: {message['gridId']}")
            print(f"   Changes: {[(c['row'], c['column'], c['newValue']) for c in message['changes']]}")
            print(f"   Timestamp: {datetime.fromtimestamp(message['timestamp']/1000)}")
            print()
            
            batch_count += 1
            
            # Wait before next update (2-5 seconds)
            time.sleep(random.uniform(0.5, 2))
            
    except KeyboardInterrupt:
        print("\n👋 Stopping producer...")
    except Exception as e:
        print(f"❌ Error: {e}")
    finally:
        try:
            producer.close()
            print("✅ Producer closed")
        except:
            pass

if __name__ == "__main__":
    main() 