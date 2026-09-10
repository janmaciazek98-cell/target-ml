import os
from PIL import Image

# ==================== KONFIGURACJA ====================
TARGET_RES = 1024  # Docelowa rozdzielczość (np. 1024 -> 1024x1024)
# ======================================================

base_dir = os.getcwd()
print(f"Przeszukiwanie folderu bazowego: {base_dir}")

# Znajdź wszystkie foldery pasujące do wzorca dataset_v2_*
dataset_folders = [
    os.path.join(base_dir, d) for d in os.listdir(base_dir)
    if d.startswith("dataset_v2_") and os.path.isdir(os.path.join(base_dir, d))
]

if not dataset_folders:
    print("Błąd: Nie znaleziono żadnych folderów pasujących do wzorca dataset_v2_*.")
    exit(0)

print(f"Znaleziono datasety do przetworzenia: {[os.path.basename(f) for f in dataset_folders]}")
target_resolution = (TARGET_RES, TARGET_RES)

for ds_path in sorted(dataset_folders):
    ds_name = os.path.basename(ds_path)
    
    # Wyszukaj wszystkie pliki PNG w folderze datasetu (również w podfolderach train/val)
    png_files = []
    for root, dirs, files in os.walk(ds_path):
        for file in files:
            if file.lower().endswith(".png"):
                png_files.append(os.path.join(root, file))

    total_files = len(png_files)
    if total_files == 0:
        print(f"\n[Pomijam] Brak plików PNG w folderze {ds_name}.")
        continue

    print(f"\nPrzetwarzanie datasetu: {ds_name} (Znaleziono {total_files} plików PNG)...")
    print(f"Skalowanie do {TARGET_RES}x{TARGET_RES} i optymalizacja...")
    
    count = 0
    for i, img_path in enumerate(png_files):
        file_name = os.path.basename(img_path)
        try:
            with Image.open(img_path) as img:
                img_resized = img.resize(target_resolution, Image.Resampling.LANCZOS)
                img_resized.save(img_path, "PNG", optimize=True, compress_level=9)
                count += 1
            
            percent = int(((i + 1) / total_files) * 100)
            print(f"[{percent}%] ({i + 1}/{total_files}) {file_name}    ", end="\r", flush=True)
            
        except Exception as e:
            print(f"\nBłąd przy pliku {file_name}: {e}")

    print(f"\n[Sukces] Zakończono dla {ds_name}: zoptymalizowano {count}/{total_files} obrazów.")

print("\nWszystkie datasety zostały pomyślnie przeskalowane i zoptymalizowane!")