# Scanner un code-barres au point de vente
feature_id: pos_barcode
language: fr

Le scanner de code-barres vous permet d'ajouter des produits au panier rapidement sans chercher manuellement dans la liste. C'est beaucoup plus rapide, surtout quand vous avez beaucoup de produits.

## Prérequis

Pour utiliser le scanner de code-barres, vous devez d'abord enregistrer le code-barres de chaque produit dans votre inventaire. Si un produit n'a pas de code-barres enregistré, le scanner ne pourra pas le trouver.

## Activer le scanner au POS

Ouvrez l'écran **POS** (Point de Vente). En haut de l'écran, près de la barre de recherche, vous verrez une icône de code-barres ou d'appareil photo. Appuyez sur cette icône pour activer le scanner.

Si c'est la première fois, l'application demandera la permission d'utiliser l'appareil photo. Appuyez sur **Autoriser**.

## Scanner un produit

Une fenêtre de scanner s'ouvre. Pointez l'appareil photo vers le code-barres du produit. Tenez le téléphone à environ 15 à 30 centimètres du code-barres.

Assurez-vous que :
- Le code-barres est bien éclairé.
- Il n'y a pas de reflet ou d'ombre sur le code-barres.
- Le code-barres est dans le cadre de visée.

L'application lit automatiquement le code-barres en quelques secondes. Vous n'avez pas besoin d'appuyer sur un bouton.

## Après le scan

Si le produit est reconnu, il s'ajoute immédiatement au panier. Vous entendrez un son de confirmation (bip). Le scanner reste actif pour scanner le produit suivant.

Si le produit n'est pas reconnu, un message s'affiche : **"Produit non trouvé"**. Cela signifie que le code-barres n'est pas encore enregistré dans votre inventaire. Vous pouvez :
- Fermer le scanner et rechercher le produit manuellement.
- Aller dans l'inventaire pour ajouter le code-barres à ce produit.

## Scanner plusieurs produits

Le scanner reste ouvert après chaque scan réussi. Vous pouvez scanner plusieurs produits à la suite sans fermer la fenêtre. Appuyez sur la flèche retour ou sur **Fermer** quand vous avez fini de scanner.

## Ajuster les quantités après le scan

Si vous scannez le même produit plusieurs fois, la quantité dans le panier augmente d'une unité à chaque scan. Vous pouvez aussi modifier la quantité directement dans le panier en appuyant sur le chiffre et en saisissant la valeur souhaitée.

## Utiliser un scanner Bluetooth externe

Si vous avez un scanner de codes-barres Bluetooth externe, vous pouvez le connecter à votre téléphone via Bluetooth. Une fois connecté, le scanner envoie les données directement à l'application POS. C'est encore plus rapide que d'utiliser l'appareil photo.

## Conseils pour de meilleurs scans

- Nettoyez l'objectif de l'appareil photo régulièrement.
- Évitez de scanner dans un environnement trop sombre.
- Si un code-barres est abîmé ou froissé, la recherche manuelle est plus fiable.
- Les codes-barres EAN-13 et QR codes sont tous les deux pris en charge.
