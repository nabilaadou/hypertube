export type Movie = {
	title: string;
	releaseYear: number;
	poster: string;
	trailerId: string;
};

export const movies = [
	{
		title: "Dead Poets Society",
		releaseYear: 1989,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/8/86/Dead_poets_society.png",
		trailerId: "aQwVQzs9pHk",
	},
	{
		title: "The Silence of the Lambs",
		releaseYear: 1991,
		poster: "https://image.tmdb.org/t/p/w500/uS9m8OBk1A8eM9I042bx8XXpqAq.jpg",
		trailerId: "6iB21hsprAQ",
	},
	{
		title: "Before Sunrise",
		releaseYear: 1995,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/d/da/Before_Sunrise_poster.jpg",
		trailerId: "6MUcuqbGTxc",
	},
	{
		title: "Taste of Cherry",
		releaseYear: 1997,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/f/fc/Tasteofcherryposter.jpg",
		trailerId: "H2NhAmDv1dw",
	},
	{
		title: "Eternal Sunshine of the Spotless Mind",
		releaseYear: 2004,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/a/a4/Eternal_Sunshine_of_the_Spotless_Mind.png",
		trailerId: "1GiLxkDK8sI",
	},
	{
		title: "(500) Days of Summer",
		releaseYear: 2009,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/d/d1/Five_hundred_days_of_summer.jpg",
		trailerId: "PsD0NpFSADM",
	},
	{
		title: "Black Swan",
		releaseYear: 2010,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/6/68/Black_Swan_poster.jpg",
		trailerId: "5jaI1XOB-bs",
	},
	{
		title: "About Time",
		releaseYear: 2013,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/7/7c/About_Time_%282013_film%29_Poster.jpg",
		trailerId: "7OIFdWk83no",
	},
	{
		title: "La La Land",
		releaseYear: 2016,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/a/ab/La_La_Land_%28film%29.png",
		trailerId: "0pdqf4P9MB8",
	},
	{
		title: "The Drama",
		releaseYear: 2026,
		poster:
			"https://upload.wikimedia.org/wikipedia/en/5/54/The_Drama_%28film%29_poster.jpg",
		trailerId: "6zmKcUa4Xxk",
	},
] satisfies [Movie, ...Movie[]];

export const cinemaMedia = {
	edit: "/media/reel.mp4",
	poster: "/media/reel-poster.jpg",
	theaterImage: "/media/theater.png",
	theaterModel: "/models/private-home-cinema-textured.glb",
};
