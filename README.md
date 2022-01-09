# MyMemory

Memory Card Game is an android app that allows user to play the default memory game at three difficulty levels: Easy, Medium, and Hard. 
The game is over after all cards on the boards are flipped and have a match. User can create a custom game with images from their phone and play their created game.
Cards are only displayed in portrait view while playing this game.

## User Stories

### Main Features
- [x] Play game by matching cards in three different levels:
    - [x] Easy board: a 2x4 grid.
    - [x] Medium board: a 6x3 grid.
    - [x] Hard board: a 6x4 grid.
    - [x] Extreme board: a 6x5 grid.
- [x] User can create as many custom games they want at all difficulty levels.
- [x] User can play their created game or share it with friends using the app.

### Other Features
- [x] Confetti to celebrate a successful game.
- [x] At the end of a successful game:
    - [x] Congratulations note is shown at the bottom with a Snackbar.
    - [x] The game's duration shown at the center of the screen with a customizable toast.
- [x] Available in 3 languages: English, French, Spanish.

## App Walk-through
Here's a walk-through of implemented user stories:

![](https://i.imgur.com/om44yu6.gif)
![](https://i.imgur.com/gZWLbyr.gif)
![](https://i.imgur.com/SQGNpUa.gif)
![](https://i.imgur.com/mLgo5ye.gif)

GIF created with [Kap](https://getkap.co).


## Notes

### Challenges encountered while building the app.
- I have no design experience, but love beautiful designs. It was challenging to come up with a good app icon and UI.
- Retrieve the coordinates, exact position of a cards to animate match found.
- Track the time while game is on

### Extension ideas
- [x] User authentication to allow them to have a list of all the custom games available
- [x] Allow user to view and play other people's custom game on authentication
- [x] Show a list of all custom games created by users

## Open-source libraries used

- [Firebase](https://console.firebase.google.com) - Save app's data: Cloud and Storage
- [Adobe Color](https://color.adobe.com/create/color-wheel) - Generate app color palette
- [Picasso](https://github.com/square/picasso) - Image downloading and caching library for Android

- App's launcher image by Gerd Altmann from Pixabay
- Flipped cards' image by Bruno DE LIMA from Pixabay


## License

    Copyright [2021] [Abravi Emiline Tekpa]
