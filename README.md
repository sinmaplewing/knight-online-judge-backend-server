<!-- PROJECT LOGO -->
# Backend Server Project of the Knight Online Judge 

<!-- PROJECT SHIELDS -->
[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![MIT License][license-shield]][license-url]


A backend server of Knight Online Judge which is written in Kotlin to manage the data of the online judge.

<!-- ABOUT THE PROJECT -->
## About The Project

This project is from the 30 days challenge which is held by iThome. About the tutorial articles, you can see them [here](https://ithelp.ithome.com.tw/articles/10233368). (in Traditional Chinese) 

### Built With
* [Kotlin](http://kotlinlang.org/)
* [Ktor](https://ktor.io)
* [Exposed](https://github.com/JetBrains/Exposed)
* [PostgreSQL](http://postgresql.org/)
* [Redis](http://redis.io)

<!-- GETTING STARTED -->
## Getting Started

First, you have to set up the PostgreSQL & Redis server in the environment.

Second, you need to add a file `/resources/hikari.properties` to set up the connection setting with the PostgreSQL server. The content in it is:

```
dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
dataSource.user=......
dataSource.password=......
dataSource.databaseName=......
dataSource.portNumber=......
dataSource.serverName=......
```

Last, run this project with the command `gradlew run`.

<!-- ROADMAP -->
## Roadmap

See the [open issues](https://github.com/sinmaplewing/knight-online-judge-backend-server/issues) for a list of proposed features (and known issues).

<!-- CONTRIBUTING -->
## Contributing

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

<!-- LICENSE -->
## License

Distributed under the MIT License. See `LICENSE` for more information.

<!-- CONTACT -->
## Contact

Maplewing - [Website](https://knightzone.studio) - sinmaplewing@gmail.com

Project Link: [https://github.com/sinmaplewing/knight-online-judge-backend-server](https://github.com/sinmaplewing/knight-online-judge-backend-server)

<!-- MARKDOWN LINKS & IMAGES -->
<!-- https://www.markdownguide.org/basic-syntax/#reference-style-links -->
[contributors-shield]: https://img.shields.io/github/contributors/sinmaplewing/knight-online-judge-backend-server
[contributors-url]: https://github.com/sinmaplewing/knight-online-judge-backend-server/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/sinmaplewing/knight-online-judge-backend-server
[forks-url]: https://github.com/sinmaplewing/knight-online-judge-backend-server/network/members
[stars-shield]: https://img.shields.io/github/stars/sinmaplewing/knight-online-judge-backend-server
[stars-url]: https://github.com/sinmaplewing/knight-online-judge-backend-server/stargazers
[issues-shield]: https://img.shields.io/github/issues/sinmaplewing/knight-online-judge-backend-server
[issues-url]: https://github.com/sinmaplewing/knight-online-judge-backend-server/issues
[license-shield]: https://img.shields.io/github/license/sinmaplewing/knight-online-judge-backend-server
[license-url]: https://github.com/sinmaplewing/knight-online-judge-backend-server/blob/master/LICENSE.txt
