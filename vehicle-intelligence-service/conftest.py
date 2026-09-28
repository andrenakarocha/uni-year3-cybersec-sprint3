import os

# O serviço exige JWT_SECRET sem default; a suíte define um segredo próprio antes de importar o app.
os.environ.setdefault("JWT_SECRET", "vehicle-tests-only-secret-0123456789abcdef")
