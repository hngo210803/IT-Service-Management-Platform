# IT Service Desk

IT Service Desk est une petite application de gestion de tickets informatiques. On peut créer un ticket, le retrouver dans la liste et changer son statut. J’ai fait ce projet pour apprendre à faire fonctionner ensemble un frontend Angular, une API Spring Boot et une base PostgreSQL avec Docker Compose.

## Lancer le projet

Il faut avoir Docker et Docker Compose. Si vous utilisez Docker Desktop, ouvrez-le avant de lancer la commande suivante à la racine du dépôt.

```bash
docker compose up --build
```

Le premier lancement peut prendre quelques minutes. Une fois les services démarrés, ouvrez [http://localhost:8081](http://localhost:8081).

Pour tester, créez un ticket intitulé `Impossible de me connecter au VPN`. Il apparaît avec le statut **Ouvert**. Passez-le à **En cours**, puis actualisez la page pour vérifier que le changement a bien été enregistré.

Pour arrêter l’application, faites `Ctrl+C` dans le terminal. Les tickets restent dans le volume PostgreSQL et seront encore là au prochain démarrage.

## Comment l’application fonctionne

- **Angular** affiche les tickets et envoie les requêtes à l’API via Nginx.
- **Spring Boot** gère la création des tickets et la mise à jour de leur statut.
- **Spring Data JPA** enregistre les données dans PostgreSQL.

## Essayer l’assistant IA

Ollama est facultatif pour gérer les tickets. Pour essayer l’assistant sur Mac, ouvrez Ollama et téléchargez les deux modèles utilisés par le projet.

```bash
ollama pull qwen3:4b
ollama pull bge-m3
```

Ensuite, essayez ces deux questions dans l’interface.

1. **« Comment me connecter au VPN ? »** L’assistant cherche dans la documentation fournie avec le projet. Vous pouvez ouvrir la source citée pour lire le passage utilisé.
2. **« Quel est le statut du ticket #1 ? »** Remplacez `1` par le numéro de votre ticket. L’assistant utilise un outil MCP pour consulter son statut actuel. Changez le statut et posez de nouveau la question pour vérifier que la réponse suit la mise à jour.

## Vérifier l’API et MCP

Avec l’application démarrée, lancez le script suivant depuis la racine du dépôt. Il faut avoir Python 3.

```bash
python3 scripts/smoke.py \
  --base-url http://127.0.0.1:8081 \
  --mcp-url http://127.0.0.1:8082/mcp
```

Le script crée un ticket de test, change son statut et vérifie que l’outil MCP renvoie le nouveau statut. GitHub Actions construit les deux images Docker et lance ce test à chaque push et pull request.
