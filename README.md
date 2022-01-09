# MyMemory

Memory Card Game is an android app that allows user to play the default memory game at four difficulty levels: Easy, Medium, Hard and Extreme.
The game is over after all cards on the boards are flipped and have a match. User can create a custom game with images from their phone and play their created game.
Cards are only displayed in portrait view while playing this game.

## User Stories

### Main Features
- Play game by matching cards in three different levels:
    - *Easy board: a 2x4 grid.*
    - *Medium board: a 6x3 grid.*
    - *Hard board: a 6x4 grid.*
    - *Extreme board: a 6x5 grid.*
- User can create as many custom games they want at all difficulty levels.
- User can play their created game or share it with friends using the app.

### Other Features
- Confetti to celebrate a successful game.
- At the end of a successful game:
    - *Congratulations note is shown at the bottom with a Snackbar.*
    - *The game's duration shown at the center of the screen with a customizable toast.*
- Available in 3 languages: English, French, Spanish.

## App Walk-through
Here's a walk-through of implemented user stories:

![playing-easy](https://i.imgur.com/om44yu6.gif)
![choose-sizes](https://i.imgur.com/gZWLbyr.gif)
![create-custom-game](https://i.imgur.com/SQGNpUa.gif)
![play-custom-game](https://i.imgur.com/mLgo5ye.gif)

GIF created with [Kap](https://getkap.co).


## Notes

### Challenges encountered while building the app.
- It was challenging to come up with a good app icon and UI because I do not have design experience.
- Retrieve the coordinates, exact position of a cards to animate match found.
- Track the time while game is on.

### Extension ideas
> - User authentication to allow them to have a list of all the custom games available
> - Allow user to view and play other people's custom game on authentication
> - Show a list of all custom games created by users

## Open-source libraries used and others

- [Firebase](https://console.firebase.google.com) - Save app's data: Cloud and Storage
- [Adobe Color](https://color.adobe.com/create/color-wheel) - Generate app color palette
- [Picasso](https://github.com/square/picasso) - Image downloading and caching library for Android
- [Pixabay](https://pixabay.com) - Free images
    - App's launcher image by Gerd Altmann
    - Flipped cards' image by Bruno DE LIMA


## License

    Copyright [2021] [Abravi Emiline Tekpa]
