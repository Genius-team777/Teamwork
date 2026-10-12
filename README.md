# Teamwork

## Команда
1. Erasul Mulaev
2. Baizhuma Rustem
3. Баха
4. Марлен

## Жоба сипаттамасы (қазақша)

Бұл жоба — жас книголюбтарға арналған танысу клубының жеңілдетілген нұсқасы.  
Негізгі мақсат: қатысушыларды тіркеу, қызығушылықтарына қарай жұп табу, әртүрлі байланыс арналары арқылы хабарлама жіберу және клуб жаңалықтарын тарату.

Жоба ішінде келесі паттерндер қолданылады:

1. **Singleton** — `ClubDatabase`  
   Қатысушылардың ортақ дерекқоры бір ғана объект ретінде жұмыс істейді.

2. **Factory** — `NotifierFactory`  
   Байланыс арнасына (`email`, `telegram`, `sms`) қарай тиісті хабарлама жіберуші объект жасайды.

3. **Strategy** — `MatchStrategy`  
   Жұп таңдау алгоритмін ауыстыруға болады (`GenreMatchStrategy`, `CityAgeMatchStrategy`).

4. **Observer** — `NewsBoard`  
   Клуб жаңалығы жарияланғанда, барлық жазылушы пайдаланушыға хабарлама жіберіледі.

Қосымша әкімшілік терезе (`AdminGUI`) арқылы:
- қатысушыны қосу/өңдеу/өшіру,
- іздеу және сүзу,
- CSV форматына экспорт/импорт жасау
мүмкіндігі бар.

## UML (Class Diagram)

```mermaid
classDiagram
    class Main

    class User {
      -name: String
      -age: int
      -city: String
      -channel: String
      -contact: String
      -genres: ArrayList~String~
      +addGenre(genre)
      +receiveNews(news)
    }

    class ClubDatabase {
      <<Singleton>>
      -users: ArrayList~User~
      +getInstance() ClubDatabase
      +register(user) boolean
      +replaceUser(old, updated) boolean
      +remove(contact) boolean
      +findByContact(contact) User
      +findByName(name) User
      +findByCity(city) ArrayList~User~
      +findByGenre(genre) ArrayList~User~
      +getUsers() ArrayList~User~
      +count() int
      +reloadIfChanged() boolean
    }

    class Notifier {
      <<interface>>
      +send(to, message)
    }
    class EmailNotifier
    class TelegramNotifier
    class SmsNotifier

    class NotifierFactory {
      +create(channel) Notifier
    }

    class MatchStrategy {
      <<interface>>
      +score(a, b) int
      +getName() String
    }
    class GenreMatchStrategy
    class CityAgeMatchStrategy

    class Matchmaker {
      -strategy: MatchStrategy
      +setStrategy(strategy)
      +printMatches(user, all)
      +findBest(user, all) User
    }

    class NewsBoard {
      -subscribers: ArrayList~User~
      +subscribe(user)
      +unsubscribe(user)
      +publish(news)
    }

    class AdminGUI

    Notifier <|.. EmailNotifier
    Notifier <|.. TelegramNotifier
    Notifier <|.. SmsNotifier

    MatchStrategy <|.. GenreMatchStrategy
    MatchStrategy <|.. CityAgeMatchStrategy

    Matchmaker --> MatchStrategy
    NotifierFactory --> Notifier
    User --> NotifierFactory
    NewsBoard --> User
    ClubDatabase --> User
    AdminGUI --> ClubDatabase
    AdminGUI --> User
    Main --> ClubDatabase
    Main --> Matchmaker
    Main --> NotifierFactory
    Main --> NewsBoard
```
