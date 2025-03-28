use std::io;
use rand::{random_range, Rng};
use std::time::Instant;

const NUM_BODIES: usize = 1000;
const STEPS: usize = 1000;
const G: f64 = 6.67430e-11;

#[derive(Copy, Clone)]
struct Body {
    x: f64,
    y: f64,
    vx: f64,
    vy: f64,
    mass: f64,
}

fn initialize_bodies() -> Vec<Body> {
    let mut rng = rand::thread_rng();
    let mut bodies = Vec::with_capacity(NUM_BODIES);

    for _ in 0..NUM_BODIES {
        bodies.push(Body {
            x: rng.gen::<f64>() * 1000.0,
            y: rng.gen::<f64>() * 1000.0,
            vx: 0.0,
            vy: 0.0,
            mass: rng.gen::<f64>() * 1000.0 + 1.0,
        });
    }

    bodies
}

fn update_bodies(bodies: &mut [Body]) {
    let mut forces = vec![(0.0, 0.0); NUM_BODIES];

    for i in 0..NUM_BODIES {
        for j in 0..NUM_BODIES {
            if i == j { continue; }

            let dx = bodies[j].x - bodies[i].x;
            let dy = bodies[j].y - bodies[i].y;
            let dist = (dx * dx + dy * dy).sqrt();
            if dist == 0.0 { continue; }

            let force = (G * bodies[i].mass * bodies[j].mass) / (dist * dist);
            forces[i].0 += force * dx / dist;
            forces[i].1 += force * dy / dist;
        }
    }

    for (i, body) in bodies.iter_mut().enumerate() {
        body.vx += forces[i].0 / body.mass;
        body.vy += forces[i].1 / body.mass;
        body.x += body.vx;
        body.y += body.vy;
    }
}


fn main() {
    let start = Instant::now();
    let mut bodies = initialize_bodies();

    for _ in 0..STEPS {
        update_bodies(&mut bodies);
    }

    let duration = start.elapsed();
    println!("Simulation complete in {:?}", duration);

    
    println!("Set min: ");
    let min: i32 = get_number(); 
    println!("Set max: ");
    let max: i32 = get_number(); 
    let randnum: i32 = random_range(min..=max);
    let mut input: i32 ;

    let mut uniquetries = 0;
    let mut numberstried: Vec<i32> = Vec::new();

    loop {
        println!("Enter: ");
        input = get_number();

        if !numberstried.contains(&input) {
            uniquetries += 1;         // Increment unique tries count
            numberstried.push(input);  // Optionally add the new input to the list
        }

        if input < randnum {
            println!("higher");
        } else if input > randnum {
            println!("lower");
        } else {
            println!("game over");
            println!("Unique tries: {uniquetries}");
            break;
        }
    }
}

fn get_number() -> i32{
    loop {
        let mut val: String = String::new();
        io::stdin().read_line(&mut val).unwrap();
        match val.trim().parse::<i32>() {
            Ok(num) => return num,
            Err(_) => println!("Please enter a number"), 
        }
    }
}