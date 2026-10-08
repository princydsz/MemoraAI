import unittest

try:
    from fastapi.testclient import TestClient
    from app.main import app, generate_embedding
    HAS_FASTAPI = True
    client = TestClient(app)
except ImportError:
    HAS_FASTAPI = False
    client = None
    generate_embedding = None

class TestAiService(unittest.TestCase):

    @unittest.skipUnless(HAS_FASTAPI, "FastAPI not installed in host environment")
    def test_embedding_direct(self):
        text1 = "Trip to Paris with my friends"
        text2 = "Trip to Paris with my friends"

        vec1 = generate_embedding(text1)
        vec2 = generate_embedding(text2)

        self.assertEqual(len(vec1), 384)
        self.assertEqual(vec1, vec2, "Same input must produce identical neural vector embeddings")

    @unittest.skipUnless(HAS_FASTAPI, "FastAPI / TestClient not installed in host environment")
    def test_health_check(self):
        response = client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["status"], "UP")
        self.assertEqual(data["service"], "MemoraAI AI Service")

    @unittest.skipUnless(HAS_FASTAPI, "FastAPI / TestClient not installed in host environment")
    def test_embeddings_endpoint(self):
        payload = {"text": "My vacation in Goa watching sunsets on Baga Beach"}
        response = client.post("/embeddings", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["dimension"], 384)
        self.assertEqual(len(data["embedding"]), 384)
        self.assertEqual(data["model"], "sentence-transformers/all-MiniLM-L6-v2")

    @unittest.skipUnless(HAS_FASTAPI, "FastAPI / TestClient not installed in host environment")
    def test_process_memory_success(self):
        payload = {
            "title": "Trip to Paris",
            "content": "Visited the Eiffel Tower and had amazing croissants at a local bakery.",
            "category": "Travel",
            "tags": "vacation,paris"
        }
        response = client.post("/process-memory", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertIn("summary", data)
        self.assertEqual(data["suggestedCategory"], "Travel")
        self.assertIn("vacation", data["suggestedTags"])

    @unittest.skipUnless(HAS_FASTAPI, "FastAPI / TestClient not installed in host environment")
    def test_process_memory_auto_categorize(self):
        payload = {
            "title": "Quarterly Review Presentation",
            "content": "Presented the Q3 results to the leadership team in the main office."
        }
        response = client.post("/process-memory", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.json()
        self.assertEqual(data["suggestedCategory"], "Work & Career")
        self.assertGreater(len(data["suggestedTags"]), 0)

if __name__ == "__main__":
    unittest.main()
