import urllib.request
import os
import ssl

# Bypass SSL certificate verification
ssl._create_default_https_context = ssl._create_unverified_context

font_dir = "app/src/main/res/font"
os.makedirs(font_dir, exist_ok=True)

fonts = {
    "plusjakartasans_regular.ttf": "https://raw.githubusercontent.com/google/fonts/main/ofl/plusjakartasans/static/PlusJakartaSans-Regular.ttf",
    "plusjakartasans_medium.ttf": "https://raw.githubusercontent.com/google/fonts/main/ofl/plusjakartasans/static/PlusJakartaSans-Medium.ttf",
    "plusjakartasans_semibold.ttf": "https://raw.githubusercontent.com/google/fonts/main/ofl/plusjakartasans/static/PlusJakartaSans-SemiBold.ttf",
    "plusjakartasans_bold.ttf": "https://raw.githubusercontent.com/google/fonts/main/ofl/plusjakartasans/static/PlusJakartaSans-Bold.ttf"
}

print("Starting font downloads with SSL bypass...")
for filename, url in fonts.items():
    dest_path = os.path.join(font_dir, filename)
    print(f"Downloading {url} to {dest_path}...")
    try:
        urllib.request.urlretrieve(url, dest_path)
        print(f"Successfully downloaded {filename}")
    except Exception as e:
        print(f"Error downloading {filename}: {e}")

print("Font download process finished.")
